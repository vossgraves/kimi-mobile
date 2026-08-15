package com.kimimobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kimimobile.data.ApiMessage
import com.kimimobile.data.AppSettings
import com.kimimobile.data.ChatApi
import com.kimimobile.data.Models
import com.kimimobile.data.SettingsStore
import com.kimimobile.data.CatalogType
import com.kimimobile.data.Marketplace
import com.kimimobile.data.Provider
import com.kimimobile.data.SkillEngine
import com.kimimobile.data.Subagent
import com.kimimobile.data.Subagents
import com.kimimobile.data.StreamChunk
import com.kimimobile.ui.components.AgentTask
import com.kimimobile.ui.components.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val time: Long = System.currentTimeMillis(),
    val failed: Boolean = false,
    val streaming: Boolean = false,
    val notice: Boolean = false,
    /** Base64 data URLs attached by the user. */
    val images: List<String> = emptyList(),
    /** Reasoning trace from thinking models, shown in a collapsible block. */
    val reasoning: String = "",
    /** Set when a subagent produced this message. */
    val agentHandle: String? = null,
)

/** Live context-window usage, estimated locally (the web API reports no usage). */
data class ContextState(
    val tokens: Long = 0,
    val maxTokens: Long = 262_144L,
    val pct: Double = 0.0,
    val messageCount: Int = 0,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val store = SettingsStore(app)
    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isAgentTurn = MutableStateFlow(false)
    val isAgentTurn: StateFlow<Boolean> = _isAgentTurn.asStateFlow()

    private val _isCompacting = MutableStateFlow(false)
    val isCompacting: StateFlow<Boolean> = _isCompacting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnected = MutableStateFlow<Boolean?>(null)
    val isConnected: StateFlow<Boolean?> = _isConnected.asStateFlow()

    private val _contextState = MutableStateFlow(ContextState())
    val contextState: StateFlow<ContextState> = _contextState.asStateFlow()

    /** Live plan for the current agent run, shown above the composer. */
    private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    /** Images staged in the composer, as base64 data URLs. */
    /** Raised when a message needs Kimi auth that isn't set up yet. */
    private val _signInRequired = MutableStateFlow(false)
    val signInRequired: StateFlow<Boolean> = _signInRequired.asStateFlow()

    private val _pendingImages = MutableStateFlow<List<String>>(emptyList())
    val pendingImages: StateFlow<List<String>> = _pendingImages.asStateFlow()

    private val api = ChatApi()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val MAX_AGENT_STEPS = 8
        private val TOOL_CALL_RE = Regex("""TOOL_CALL:([a-z_0-9]+)\|([^\n]*)""")
        private val PLAN_RE = Regex("""PLAN:\s*([^\n]+)""")
        private val STEP_DONE_RE = Regex("""STEP_DONE:\s*(\d+)""")

        /** Rough token estimate: ~4 chars/token + per-message overhead. */
        fun estimateTokens(texts: List<String>): Long =
            texts.sumOf { it.length.toLong() } / 4L + texts.size * 8L
    }

    // ---- Settings passthroughs --------------------------------------------

    fun setModel(id: String) {
        viewModelScope.launch {
            store.setModel(id)
            // Keep the context ring honest about the new model's window.
            store.setMaxContextTokens(Models.contextTokensFor(id))
        }
    }

    fun setSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setSearchEnabled(enabled) }
    }

    fun setResearchEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setResearchEnabled(enabled) }
    }

    fun setMathEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setMathEnabled(enabled) }
    }

    fun setUpdateChannel(channel: String) {
        viewModelScope.launch { store.setUpdateChannel(channel) }
    }

    fun setMaxContextTokens(max: Long) {
        viewModelScope.launch { store.setMaxContextTokens(max.coerceAtLeast(1000L)) }
    }

    fun setAutoCompact(enabled: Boolean) {
        viewModelScope.launch { store.setAutoCompact(enabled) }
    }

    fun setCompactThreshold(pct: Int) {
        viewModelScope.launch { store.setCompactThreshold(pct.coerceIn(40, 95)) }
    }

    fun setAgentEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setAgentEnabled(enabled) }
    }

    fun toggleSkill(id: String) {
        val current = settings.value.installedSkills
        val next = if (id in current) current - id else current + id
        viewModelScope.launch { store.setInstalledSkills(next) }
    }

    // ---- Attachments ---------------------------------------------------------

    fun attachImage(dataUrl: String) {
        _pendingImages.update { (it + dataUrl).takeLast(4) }
    }

    fun removeImage(dataUrl: String) {
        _pendingImages.update { it - dataUrl }
    }

    fun clearImages() {
        _pendingImages.value = emptyList()
    }

    // ---- Chat ----------------------------------------------------------------

    /** The model id actually sent, including capability suffixes. */
    private fun resolvedModel(cfg: AppSettings): String = Models.resolve(
        baseId = cfg.model,
        search = cfg.searchEnabled,
        research = cfg.researchEnabled,
        math = cfg.mathEnabled,
    )

    fun send(text: String) {
        val cfg = settings.value
        val images = _pendingImages.value
        if ((text.isBlank() && images.isEmpty()) || _isStreaming.value) return
        // Kimi needs a token; Zen free models don't. Ask for sign-in only when
        // the selected model actually requires it.
        val needsKimiAuth = Models.providerOf(cfg.model) == Provider.KIMI && cfg.token.isBlank()
        if (needsKimiAuth) {
            _signInRequired.value = true
            return
        }
        // "add web search", "turn off memory" — manage tools by asking.
        Marketplace.parseInstallIntent(text)?.let { intent ->
            applyInstallIntent(intent)
            return
        }

        val mention = Subagents.parseMention(text)
        when {
            mention != null -> sendToSubagent(mention.first, mention.second, cfg)
            cfg.agentEnabled -> sendAgent(text, cfg)
            else -> sendChat(text, cfg, images)
        }
        clearImages()
    }

    private fun sendChat(text: String, cfg: AppSettings, images: List<String>) {
        // Attachments imply vision: swap models for this turn rather than
        // making you pick a vision model by hand.
        val effectiveModel = if (images.isNotEmpty() &&
            Models.byId(cfg.model)?.vision != true &&
            Models.providerOf(cfg.model) == Provider.KIMI
        ) {
            Models.visionModelFor(cfg.maxContextTokens).id
        } else {
            cfg.model
        }
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = text.trim(),
            images = images,
        )
        val placeholder = ChatMessage(role = MessageRole.ASSISTANT, content = "", streaming = true)
        _messages.update { it + userMsg + placeholder }
        refreshContext()

        val history = _messages.value
            .filter { it.id != placeholder.id && !it.failed }
            .map { ApiMessage(role = it.role.name.lowercase(), content = it.content, images = it.images) }

        _isStreaming.value = true
        _isConnected.value = null
        viewModelScope.launch {
            try {
                val model = Models.resolve(
                    baseId = effectiveModel,
                    search = cfg.searchEnabled,
                    research = cfg.researchEnabled,
                    math = cfg.mathEnabled,
                )
                val (baseUrl, token) = endpointFor(effectiveModel, cfg)
                api.streamChat(baseUrl, token, model, history).collect { chunkJson ->
                    val delta = runCatching {
                        json.decodeFromString<StreamChunk>(chunkJson).choices.firstOrNull()?.delta
                    }.getOrNull() ?: return@collect
                    val content = delta.content.orEmpty()
                    val reasoning = delta.reasoningContent.orEmpty()
                    if (content.isNotEmpty() || reasoning.isNotEmpty()) {
                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == placeholder.id) {
                                    msg.copy(
                                        content = msg.content + content,
                                        reasoning = msg.reasoning + reasoning,
                                    )
                                } else msg
                            }
                        }
                    }
                }
                finalizeAssistant(placeholder, null)
                maybeAutoCompact()
            } catch (e: Exception) {
                failPlaceholder(placeholder, e)
            } finally {
                _isStreaming.value = false
            }
        }
    }

    // ---- Agent mode (prompt-protocol tool loop) ------------------------------

    private fun sendAgent(text: String, cfg: AppSettings) {
        val userMsg = ChatMessage(role = MessageRole.USER, content = text.trim())
        val placeholder = ChatMessage(role = MessageRole.ASSISTANT, content = "", streaming = true)
        _messages.update { it + userMsg + placeholder }
        refreshContext()

        _isStreaming.value = true
        _isAgentTurn.value = true
        _isConnected.value = null
        _tasks.value = emptyList()
        viewModelScope.launch {
            try {
                val model = resolvedModel(cfg)
                val (baseUrl, token) = endpointFor(cfg.model, cfg)
                val history = mutableListOf<ApiMessage>()
                history += ApiMessage("system", agentSystemPrompt(cfg.installedSkills))
                history += _messages.value
                    .filter { it.id != placeholder.id && it.content.isNotBlank() }
                    .map { ApiMessage(role = it.role.name.lowercase(), content = it.content) }

                loop@ for (step in 1..MAX_AGENT_STEPS) {
                    val resp = api.complete(baseUrl, token, model, history)
                    if (resp.isBlank()) break@loop
                    applyPlanUpdates(resp)
                    val visible = stripProtocolLines(resp)
                    if (visible.isNotBlank()) {
                        _messages.update { list ->
                            list.map {
                                if (it.id == placeholder.id) it.copy(content = it.content + visible)
                                else it
                            }
                        }
                    }
                    history += ApiMessage("assistant", resp)

                    // Subagent delegation — independent tasks run in parallel.
                    val delegations = Subagents.DELEGATE_RE.findAll(resp)
                        .mapNotNull { m ->
                            Subagents.byHandle(m.groupValues[1])?.let { it to m.groupValues[2].trim() }
                        }
                        .toList()
                    if (delegations.isNotEmpty()) {
                        val reports = coroutineScope {
                            delegations.map { (agent, task) ->
                                async {
                                    val out = runCatching { runSubagent(agent, task, cfg) }
                                        .getOrElse { "Error: ${it.message}" }
                                    agent to out
                                }
                            }.awaitAll()
                        }
                        reports.forEach { (agent, out) ->
                            history += ApiMessage("user", "AGENT_REPORT:${agent.handle}|$out")
                            _messages.update { list ->
                                list.map {
                                    if (it.id == placeholder.id) {
                                        it.copy(
                                            content = it.content +
                                                "\n\n```agent\n@${agent.handle}\n${out.take(700)}\n```\n"
                                        )
                                    } else it
                                }
                            }
                        }
                        refreshContext()
                        continue@loop
                    }

                    val calls = TOOL_CALL_RE.findAll(resp).map { m ->
                        m.groupValues[1] to m.groupValues[2].trim()
                    }.toList()
                    if (calls.isEmpty()) break@loop

                    for ((id, args) in calls) {
                        val skill = SkillEngine.byId(id)
                        val result = if (skill != null) {
                            runCatching { skill.run(args) }
                                .getOrElse { "Error: ${it.message ?: "tool failed"}" }
                        } else {
                            "Error: unknown tool \"$id\""
                        }
                        history += ApiMessage("user", "TOOL_RESULT:$id|$result")
                        _messages.update { list ->
                            list.map {
                                if (it.id == placeholder.id) {
                                    it.copy(
                                        content = it.content +
                                            "\n\n```tool\n$id: ${args.take(80)}\n→ ${result.take(500)}\n```\n"
                                    )
                                } else it
                            }
                        }
                        refreshContext()
                    }
                }
                _tasks.update { list -> list.map { it.copy(status = TaskStatus.DONE) } }
                finalizeAssistant(placeholder, null)
                maybeAutoCompact()
            } catch (e: Exception) {
                failPlaceholder(placeholder, e)
            } finally {
                _isStreaming.value = false
                _isAgentTurn.value = false
            }
        }
    }

    /**
     * Reads PLAN: / STEP_DONE: lines the agent emits and drives the task bar.
     * Declaring a plan is optional — the bar just stays hidden without one.
     */
    private fun applyPlanUpdates(chunk: String) {
        val planned = PLAN_RE.findAll(chunk)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (planned.isNotEmpty() && _tasks.value.isEmpty()) {
            _tasks.value = planned.mapIndexed { index, text ->
                AgentTask(text, if (index == 0) TaskStatus.ACTIVE else TaskStatus.PENDING)
            }
        }
        STEP_DONE_RE.findAll(chunk).forEach { match ->
            val oneBased = match.groupValues[1].toIntOrNull() ?: return@forEach
            val index = oneBased - 1
            _tasks.update { list ->
                if (index !in list.indices) list
                else list.mapIndexed { i, task ->
                    when {
                        i == index -> task.copy(status = TaskStatus.DONE)
                        i == index + 1 && task.status == TaskStatus.PENDING ->
                            task.copy(status = TaskStatus.ACTIVE)
                        else -> task
                    }
                }
            }
        }
    }

    /** PLAN:/STEP_DONE:/TOOL_CALL: lines drive the UI, not the transcript. */
    private fun stripProtocolLines(text: String): String =
        text.lineSequence()
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("PLAN:") || t.startsWith("STEP_DONE:") ||
                    t.startsWith("TOOL_CALL:") || t.startsWith("DELEGATE:")
            }
            .joinToString("\n")
            .trim()

    private fun applyInstallIntent(intent: Marketplace.InstallIntent) {
        val item = intent.item
        val installed = settings.value.installedSkills
        val already = item.id in installed
        val next = if (intent.enable) installed + item.id else installed - item.id

        val note = when {
            intent.enable && already -> "**${item.name}** is already enabled."
            !intent.enable && !already -> "**${item.name}** wasn't enabled."
            intent.enable -> if (item.type == CatalogType.SKILL) {
                "Enabled **${item.name}** — the agent can use it now."
            } else {
                "Marked **${item.name}** as wanted. Connectors are MCP servers that run on " +
                    "your own machine; this app can't launch them yet, so point your MCP host " +
                    "at `${item.url}` to actually use it."
            }
            else -> "Disabled **${item.name}**."
        }

        _messages.update { list ->
            list +
                ChatMessage(role = MessageRole.USER, content = intentEcho(intent)) +
                ChatMessage(role = MessageRole.ASSISTANT, content = note, notice = true)
        }
        viewModelScope.launch { store.setInstalledSkills(next) }
        refreshContext()
    }

    private fun intentEcho(intent: Marketplace.InstallIntent): String =
        if (intent.enable) "Enable ${intent.item.name}" else "Disable ${intent.item.name}"

    /** Zen's free models need no key; Kimi goes through the proxy. */
    private fun endpointFor(modelId: String, cfg: AppSettings): Pair<String, String> =
        if (Models.providerOf(modelId) == Provider.ZEN) {
            // Free models answer without a key; paid ones use the one you add.
            Models.ZEN_BASE_URL to cfg.zenApiKey
        } else {
            cfg.baseUrl to cfg.token
        }

    /** Runs one subagent to completion and returns its report. */
    private suspend fun runSubagent(
        agent: Subagent,
        task: String,
        cfg: AppSettings,
        onStep: (String) -> Unit = {},
    ): String {
        val model = agent.preferredModel ?: cfg.model
        val (baseUrl, token) = endpointFor(model, cfg)
        val tools = SkillEngine.all.filter { it.id in agent.tools && it.id in cfg.installedSkills }
        val toolList = tools.joinToString("\n") { "- ${it.id}: ${it.description}" }

        val history = mutableListOf(
            ApiMessage(
                role = "system",
                content = buildString {
                    append(agent.systemPrompt)
                    if (tools.isNotEmpty()) {
                        append("\n\nTOOL PROTOCOL: to use a tool, output one line:\n")
                        append("TOOL_CALL:<tool>|<args>\nAvailable tools:\n")
                        append(toolList)
                        append("\nA TOOL_RESULT line will follow. Stop calling tools once you can answer.")
                    }
                },
            ),
            ApiMessage(role = "user", content = task),
        )

        var report = ""
        loop@ for (step in 1..5) {
            val resp = api.complete(baseUrl, token, model, history, maxTokens = 2048)
            if (resp.isBlank()) break@loop
            history += ApiMessage("assistant", resp)
            report = stripProtocolLines(resp)

            val calls = TOOL_CALL_RE.findAll(resp)
                .map { it.groupValues[1] to it.groupValues[2].trim() }
                .toList()
            if (calls.isEmpty()) break@loop

            for ((id, args) in calls) {
                onStep("${agent.name}: $id")
                val skill = tools.firstOrNull { it.id == id }
                val result = if (skill != null) {
                    runCatching { skill.run(args) }.getOrElse { "Error: ${it.message}" }
                } else {
                    "Error: \"$id\" is not available to this agent"
                }
                history += ApiMessage("user", "TOOL_RESULT:$id|$result")
            }
        }
        return report.ifBlank { "(no output)" }
    }

    /** Direct delegation from the chat box: "@researcher ..." */
    private fun sendToSubagent(agent: Subagent, task: String, cfg: AppSettings) {
        val userMsg = ChatMessage(role = MessageRole.USER, content = "@${agent.handle} $task")
        val placeholder = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            streaming = true,
            agentHandle = agent.handle,
        )
        _messages.update { it + userMsg + placeholder }
        _isStreaming.value = true
        _isAgentTurn.value = true
        _tasks.value = listOf(AgentTask("${agent.name} working…", TaskStatus.ACTIVE))
        viewModelScope.launch {
            try {
                val report = runSubagent(agent, task, cfg) { step ->
                    _tasks.value = listOf(AgentTask(step, TaskStatus.ACTIVE))
                }
                _messages.update { list ->
                    list.map {
                        if (it.id == placeholder.id) it.copy(content = report, streaming = false)
                        else it
                    }
                }
                _tasks.update { list -> list.map { it.copy(status = TaskStatus.DONE) } }
                _isConnected.value = true
                refreshContext()
            } catch (e: Exception) {
                failPlaceholder(placeholder, e)
            } finally {
                _isStreaming.value = false
                _isAgentTurn.value = false
            }
        }
    }

    private fun agentSystemPrompt(installed: Set<String>): String {
        val tools = SkillEngine.all
            .filter { it.id in installed }
            .joinToString("\n") { "- ${it.id}: ${it.description}" }
        return buildString {
            append("You are a capable coding and research agent running on an Android client. ")
            append("Solve the user's task step by step. You have tools for external information.\n")
            append("PLAN PROTOCOL: before you start, list your steps, one per line:\n")
            append("PLAN: <short step description>\n")
            append("Then as you finish each step, output: STEP_DONE:<step number>\n")
            append("Keep plans to 2-6 steps. Skip the plan for trivial one-step answers.\n\n")
            append("TOOL PROTOCOL: when you need data, output exactly one line:\n")
            append("TOOL_CALL:<tool>|<args>\n")
            append("Available tools:\n")
            append(if (tools.isBlank()) "(none installed)" else tools)
            append("\n\nDELEGATION: for a self-contained chunk of work, hand it to a subagent:\n")
            append("DELEGATE:<handle>|<task>\n")
            append("Available subagents:\n")
            append(Subagents.all.joinToString("\n") { "- ${it.handle}: ${it.description}" })
            append("\nIssue several DELEGATE lines at once and they run in parallel. ")
            append("Each replies with AGENT_REPORT. Delegate research and heavy reading so ")
            append("this conversation stays focused.\n")
            append("\nAfter each tool runs, a TOOL_RESULT line will appear — use it and continue. ")
            append("Never invent tool output; if a tool errors, say so and try another way. ")
            append("When the task is complete, give a concise final answer and stop calling tools.")
        }
    }

    // ---- Context window & compaction ----------------------------------------

    private fun refreshContext() {
        val cfg = settings.value
        val texts = _messages.value.filter { it.content.isNotBlank() }.map { it.content }
        // Images cost roughly a thousand tokens each once tiled.
        val imageTokens = _messages.value.sumOf { it.images.size } * 1024L
        val tokens = estimateTokens(texts) + imageTokens + 1000L
        val max = cfg.maxContextTokens.coerceAtLeast(1000L)
        _contextState.value = ContextState(
            tokens = tokens,
            maxTokens = max,
            pct = tokens.toDouble() / max,
            messageCount = texts.size,
        )
    }

    /** Compacts when auto-compact is on and we're past the threshold. */
    private fun maybeAutoCompact() {
        val cfg = settings.value
        if (cfg.autoCompact && _contextState.value.pct * 100.0 >= cfg.compactThresholdPct) {
            compactNow()
        }
    }

    /** Summarizes older turns into one notice message, keeping the last pair. */
    fun compactNow() {
        val cfg = settings.value
        if (_isCompacting.value || cfg.token.isBlank()) return
        val history = _messages.value
            .filter { !it.notice && it.content.isNotBlank() }
            .map { ApiMessage(role = it.role.name.lowercase(), content = it.content) }
        if (history.size < 4) return // nothing worth compacting

        _isCompacting.value = true
        viewModelScope.launch {
            try {
                val summary = api.complete(
                    cfg.baseUrl, cfg.token, cfg.model,
                    history + ApiMessage(
                        role = "user",
                        content = "Summarize this conversation for continuity. Keep all decisions, " +
                            "code snippets, user preferences, and open questions. " +
                            "Output a structured summary of max 600 words."
                    ),
                    maxTokens = 2048,
                )
                if (summary.isBlank()) return@launch
                val keep = _messages.value.takeLast(2)
                val notice = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = summary,
                    notice = true,
                )
                _messages.value = listOf(notice) + keep
                refreshContext()
            } catch (e: Exception) {
                _error.value = "Compaction failed: ${e.message}"
            } finally {
                _isCompacting.value = false
            }
        }
    }

    // ---- Helpers ---------------------------------------------------------------

    private fun finalizeAssistant(placeholder: ChatMessage, error: String?) {
        _messages.update { list ->
            list.map {
                if (it.id == placeholder.id) it.copy(streaming = false, failed = error != null)
                else it
            }
        }
        refreshContext()
        _isConnected.value = error == null
        if (error != null) _error.value = error
    }

    private fun failPlaceholder(placeholder: ChatMessage, e: Exception) {
        finalizeAssistant(placeholder, e.message ?: "Request failed")
    }

    /** Retries the last user turn after a failure. */
    fun retryLast() {
        val lastUser = _messages.value.lastOrNull { it.role == MessageRole.USER } ?: return
        _messages.update { list ->
            val idx = list.indexOfLast { it.role == MessageRole.USER }
            if (idx < 0) list else list.take(idx)
        }
        _pendingImages.value = lastUser.images
        send(lastUser.content)
    }

    /** Non-network warm check: pings the API only if we haven't connected yet. */
    fun ensureConnected() {
        if (_isConnected.value == null && settings.value.token.isNotBlank()) {
            viewModelScope.launch { testConnection() }
        }
    }

    /** Tests connectivity to (optionally overridden) settings. Returns success. */
    suspend fun testConnection(
        baseUrl: String = settings.value.baseUrl,
        token: String = settings.value.token,
        model: String = settings.value.model,
    ): Boolean {
        if (token.isBlank() && Models.providerOf(model) == Provider.KIMI) {
            _error.value = "Sign in first — no refresh token yet"
            return false
        }
        return try {
            // Zen free models need no token at all.
            if (Models.providerOf(model) == Provider.ZEN) {
                api.listModels(Models.ZEN_BASE_URL, settings.value.zenApiKey)
                _error.value = null
                _isConnected.value = true
                return true
            }
            val live = api.checkToken(baseUrl, token)
            _error.value = if (live) null else "Token rejected — sign in again"
            _isConnected.value = live
            live
        } catch (e: Exception) {
            _error.value = e.message ?: "Connection failed"
            _isConnected.value = false
            false
        }
    }

    fun clear() {
        _messages.value = emptyList()
        _error.value = null
        _tasks.value = emptyList()
        clearImages()
        refreshContext()
    }

    fun clearError() {
        _error.value = null
    }

    fun dismissSignIn() {
        _signInRequired.value = false
    }
}
