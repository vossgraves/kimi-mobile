package com.kimimobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kimimobile.data.ApiMessage
import com.kimimobile.data.AppSettings
import com.kimimobile.data.ChatApi
import com.kimimobile.data.KimiModel
import com.kimimobile.data.ModelCatalog
import com.kimimobile.data.Models
import com.kimimobile.data.ProxyDiscovery
import com.kimimobile.data.SettingsStore
import com.kimimobile.data.AgentMode
import com.kimimobile.data.CapabilityRouter
import com.kimimobile.data.CatalogType
import com.kimimobile.data.Conversation
import com.kimimobile.data.ConversationStore
import com.kimimobile.data.ConversationSummary
import com.kimimobile.data.ReasoningEffort
import com.kimimobile.data.StoredMessage
import com.kimimobile.service.WakeLockService
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

/** Accumulated usage for this session, from the cost/usage each reply carries. */
data class SessionSpend(
    val tokens: Long = 0,
    val costUsd: Double = 0.0,
    val requests: Int = 0,
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
    val conversations = ConversationStore(app)

    /** Conversation currently on screen; a new one until it's first saved. */
    private val _activeConversationId = MutableStateFlow(java.util.UUID.randomUUID().toString())
    val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()
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
    private val _discoveringProxy = MutableStateFlow(false)
    val discoveringProxy: StateFlow<Boolean> = _discoveringProxy.asStateFlow()

    /** Re-runs proxy discovery on demand. */
    fun rediscoverProxy() {
        viewModelScope.launch {
            _discoveringProxy.value = true
            val found = ProxyDiscovery.discover(settings.value.baseUrl)
            _error.value = if (found != null) {
                store.setBaseUrl(found)
                "Found proxy at $found"
            } else {
                "No proxy found — start it, or set the URL manually"
            }
            _discoveringProxy.value = false
        }
    }

    /** Running spend for this session, shown next to the composer. */
    private val _sessionSpend = MutableStateFlow(SessionSpend())
    val sessionSpend: StateFlow<SessionSpend> = _sessionSpend.asStateFlow()

    /** Live model list; falls back to the built-in catalogue offline. */
    private val _availableModels = MutableStateFlow(Models.all)
    val availableModels: StateFlow<List<KimiModel>> = _availableModels.asStateFlow()

    /** Raised when a message needs Kimi auth that isn't set up yet. */
    private val _signInRequired = MutableStateFlow(false)
    val signInRequired: StateFlow<Boolean> = _signInRequired.asStateFlow()

    private val _pendingImages = MutableStateFlow<List<String>>(emptyList())
    val pendingImages: StateFlow<List<String>> = _pendingImages.asStateFlow()

    private val api = ChatApi()

    init {
        api.onSpend = { report ->
            _sessionSpend.update { current ->
                current.copy(
                    tokens = current.tokens + report.promptTokens + report.completionTokens,
                    costUsd = current.costUsd + report.costUsd,
                    requests = current.requests + 1,
                )
            }
        }
        viewModelScope.launch { conversations.refresh() }
        // Find the proxy automatically so nobody has to type a URL, and
        // again whenever the token changes — signing in used to leave the
        // app pointed at an unreachable default and report "connection
        // failed" right after a successful login.
        viewModelScope.launch {
            var lastToken: String? = null
            settings.collect { cfg ->
                if (!cfg.loaded) return@collect
                val tokenChanged = lastToken != null && lastToken != cfg.token
                val firstRun = lastToken == null
                lastToken = cfg.token
                if ((firstRun && (cfg.proxyAutoDetected || cfg.baseUrl.isBlank())) || tokenChanged) {
                    _discoveringProxy.value = true
                    val found = ProxyDiscovery.discover(cfg.baseUrl.takeIf { it.isNotBlank() })
                    if (found != null && found != cfg.baseUrl) {
                        store.setBaseUrl(found)
                    }
                    _discoveringProxy.value = false
                    if (tokenChanged && cfg.token.isNotBlank()) {
                        _isConnected.value = null // let the next probe re-check
                    }
                }
            }
        }
        // Pull the real model list as soon as settings are available.
        viewModelScope.launch {
            settings.collect { cfg ->
                if (cfg.baseUrl.isNotBlank() && _availableModels.value === Models.all) {
                    refreshModels(cfg)
                }
            }
        }
    }

    private suspend fun refreshModels(cfg: AppSettings) {
        val models = ModelCatalog.load(cfg.baseUrl, cfg.token, cfg.zenApiKey)
        _availableModels.value = models
    }

    fun reloadModels() {
        viewModelScope.launch {
            val cfg = settings.value
            _availableModels.value = ModelCatalog.load(cfg.baseUrl, cfg.token, cfg.zenApiKey, force = true)
        }
    }
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
            // A failed request leaves the connection flagged bad; switching
            // models should clear that so the new one isn't judged by it.
            _isConnected.value = null
            _error.value = null
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

    fun completeOnboarding(defaultToFreeModel: Boolean) {
        viewModelScope.launch {
            if (defaultToFreeModel) {
                // Free Zen model works with no account at all.
                store.setModel("nemotron-3.5-lightning-free")
                store.setMaxContextTokens(262_144L)
            }
            store.setOnboarded(true)
        }
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

        val mode = AgentMode.byId(cfg.agentMode)
        val mention = Subagents.parseMention(text)
        when {
            mention != null -> sendToSubagent(mention.first, mention.second, cfg)
            mode.usesTools -> sendAgent(text, cfg, mode)
            else -> sendChat(text, cfg, images)
        }
        clearImages()
    }

    private fun sendChat(text: String, cfg: AppSettings, images: List<String>) {
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = text.trim(),
            images = images,
        )
        val placeholder = ChatMessage(role = MessageRole.ASSISTANT, content = "", streaming = true)
        _messages.update { it + userMsg + placeholder }
        refreshContext()

        _isStreaming.value = true
        _isConnected.value = null
        viewModelScope.launch {
            try {
                // Images: use the chosen model if it sees, otherwise borrow
                // Kimi vision and feed its description back to the real model.
                var describedImages: String? = null
                if (images.isNotEmpty()) {
                    val route = CapabilityRouter.routeVision(cfg.model, cfg)
                    if (route == null) {
                        finalizeAssistant(placeholder, CapabilityRouter.NO_VISION_HELP)
                        return@launch
                    }
                    if (route.borrowed) {
                        _tasks.value = listOf(
                            AgentTask("Reading image with Kimi vision", TaskStatus.ACTIVE)
                        )
                        val description = api.complete(
                            baseUrl = route.baseUrl,
                            token = route.token,
                            model = route.modelId,
                            messages = listOf(
                                ApiMessage(
                                    role = "user",
                                    content = CapabilityRouter.DESCRIBE_PROMPT,
                                    images = images,
                                )
                            ),
                            maxTokens = 1500,
                        )
                        describedImages = CapabilityRouter.describedContext(description)
                        _tasks.update { list -> list.map { it.copy(status = TaskStatus.DONE) } }
                    }
                }

                // When vision was borrowed the images become text, so the
                // chosen model still answers the actual question.
                val history = _messages.value
                    .filter { it.id != placeholder.id && !it.failed }
                    .map { msg ->
                        val isLatestUser = msg.id == userMsg.id
                        ApiMessage(
                            role = msg.role.name.lowercase(),
                            content = if (isLatestUser && describedImages != null) {
                                buildString {
                                    append(describedImages)
                                    if (msg.content.isNotBlank()) append("\n\n").append(msg.content)
                                }
                            } else msg.content,
                            images = if (describedImages != null) emptyList() else msg.images,
                        )
                    }

                // The free Zen pool rate-limits per model; when the chosen one
                // is saturated, fail over to the next free model instead of
                // dying — the user asked a question, not for an error.
                val candidates = buildList {
                    add(cfg.model)
                    val current = Models.byId(cfg.model)
                    if (current?.provider == Provider.ZEN && !current.requiresKey) {
                        _availableModels.value
                            .filter {
                                it.provider == Provider.ZEN && !it.requiresKey &&
                                    it.id != cfg.model
                            }
                            .take(2)
                            .forEach { add(it.id) }
                    }
                }

                var lastRateLimit: Exception? = null
                var served = false
                candidateLoop@ for ((index, candidate) in candidates.withIndex()) {
                    val model = Models.resolve(
                        baseId = candidate,
                        search = cfg.searchEnabled,
                        research = cfg.researchEnabled,
                        math = cfg.mathEnabled,
                    )
                    val (baseUrl, token) = endpointFor(candidate, cfg)
                    val effort = if (Models.byId(candidate)?.reasoning == true) {
                        ReasoningEffort.byId(cfg.reasoningEffort).id
                    } else null
                    try {
                        if (index > 0) {
                            _error.value = "${Models.byId(cfg.model)?.name} is rate-limited — " +
                                "answering with ${Models.byId(candidate)?.name}"
                            // Drop anything a failed candidate managed to stream.
                            _messages.update { list ->
                                list.map {
                                    if (it.id == placeholder.id) it.copy(content = "", reasoning = "")
                                    else it
                                }
                            }
                        }
                        streamInto(placeholder, baseUrl, token, model, history, effort)
                        served = true
                        break@candidateLoop
                    } catch (e: Exception) {
                        val limited = e.message?.contains("rate-limit", true) == true ||
                            e.message?.contains("Rate limit", true) == true
                        if (limited && index < candidates.lastIndex) {
                            lastRateLimit = e
                            continue@candidateLoop
                        }
                        throw e
                    }
                }
                if (!served) throw (lastRateLimit ?: IllegalStateException("No model available"))
                finalizeAssistant(placeholder, null)
                maybeAutoCompact()
            } catch (e: Exception) {
                failPlaceholder(placeholder, e)
            } finally {
                _isStreaming.value = false
                _tasks.value = emptyList()
            }
        }
    }

    /** Streams one completion into the placeholder message. */
    private suspend fun streamInto(
        placeholder: ChatMessage,
        baseUrl: String,
        token: String,
        model: String,
        history: List<ApiMessage>,
        effort: String?,
    ) {
        api.streamChat(baseUrl, token, model, history, effort).collect { chunkJson ->
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
    }

    // ---- Agent mode (prompt-protocol tool loop) ------------------------------

    private fun sendAgent(text: String, cfg: AppSettings, mode: AgentMode) {
        val userMsg = ChatMessage(role = MessageRole.USER, content = text.trim())
        val placeholder = ChatMessage(role = MessageRole.ASSISTANT, content = "", streaming = true)
        _messages.update { it + userMsg + placeholder }
        refreshContext()

        _isStreaming.value = true
        _isAgentTurn.value = true
        _isConnected.value = null
        _tasks.value = emptyList()
        // Agent runs are long; don't let the screen going off kill them.
        if (cfg.keepAwake) WakeLockService.start(getApplication(), "Agent run in progress")
        viewModelScope.launch {
            try {
                val model = resolvedModel(cfg)
                val (baseUrl, token) = endpointFor(cfg.model, cfg)
                val history = mutableListOf<ApiMessage>()
                history += ApiMessage("system", agentSystemPrompt(cfg, mode))
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

                    // Models that ignore PLAN: still get a visible task list,
                    // built from the tools they actually invoke.
                    if (_tasks.value.isEmpty()) {
                        _tasks.value = calls.map { (id, _) ->
                            AgentTask(SkillEngine.byId(id)?.name ?: id, TaskStatus.PENDING)
                        }
                    }

                    for ((index, call) in calls.withIndex()) {
                        val (id, args) = call
                        _tasks.update { list ->
                            list.mapIndexed { i, task ->
                                when {
                                    task.text == (SkillEngine.byId(id)?.name ?: id) &&
                                        task.status == TaskStatus.PENDING ->
                                        task.copy(status = TaskStatus.ACTIVE)
                                    else -> task
                                }
                            }
                        }
                        val allowedTools = AgentMode.toolsFor(mode, cfg.installedSkills)
                        val skill = SkillEngine.byId(id)?.takeIf { it.id in allowedTools }
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
                        _tasks.update { list ->
                            list.map { task ->
                                if (task.text == (skill?.name ?: id) && task.status == TaskStatus.ACTIVE) {
                                    task.copy(status = TaskStatus.DONE)
                                } else task
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
                WakeLockService.stop(getApplication())
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
            // opencode CLI sends the literal key "public" when anonymous —
            // matching it keeps us in the same request class it uses.
            Models.ZEN_BASE_URL to cfg.zenApiKey.ifBlank { "public" }
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
                WakeLockService.stop(getApplication())
            }
        }
    }

    private fun agentSystemPrompt(cfg: AppSettings, mode: AgentMode): String {
        val allowed = AgentMode.toolsFor(mode, cfg.installedSkills)
        val tools = SkillEngine.all
            .filter { it.id in allowed }
            .joinToString("\n") { "- ${it.id}: ${it.description}" }
        return buildString {
            append(mode.prompt).append("\n\n")
            append("PLAN PROTOCOL: before you start, list your steps, one per line:\n")
            append("PLAN: <short step description>\n")
            append("Then as you finish each step, output: STEP_DONE:<step number>\n")
            append("Keep plans to 2-6 steps. Skip the plan for trivial one-step answers.\n\n")
            append("TOOL PROTOCOL: when you need data, output exactly one line:\n")
            append("TOOL_CALL:<tool>|<args>\n")
            append("Available tools:\n")
            append(if (tools.isBlank()) "(none installed)" else tools)
            if (!mode.canDelegate) return@buildString
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
        if (_isCompacting.value) return
        val cfg = settings.value
        val (baseUrl, token) = endpointFor(cfg.model, cfg)
        // Zen free models need no token; only Kimi does.
        if (token.isBlank() && Models.providerOf(cfg.model) == Provider.KIMI) {
            _error.value = "Sign in before compacting"
            return
        }

        val history = _messages.value
            .filter { !it.notice && it.content.isNotBlank() }
            .map { ApiMessage(role = it.role.name.lowercase(), content = it.content) }
        if (history.size < 4) {
            _error.value = "Not enough conversation to compact yet"
            return
        }

        _isCompacting.value = true
        viewModelScope.launch {
            try {
                val summary = api.complete(
                    baseUrl = baseUrl,
                    token = token,
                    model = Models.resolve(cfg.model),
                    messages = history + ApiMessage(
                        role = "user",
                        content = "Summarize this conversation for continuity. Keep all decisions, " +
                            "code snippets, user preferences, and open questions. " +
                            "Output a structured summary of max 600 words."
                    ),
                    maxTokens = 2048,
                )
                if (summary.isBlank()) {
                    // Don't destroy history when the model returns nothing.
                    _error.value = "Compaction returned nothing — history left untouched"
                    return@launch
                }
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

    /** Writes the current thread to disk so it shows up in the drawer. */
    private fun persistConversation() {
        val snapshot = _messages.value
        if (snapshot.isEmpty()) return
        val id = _activeConversationId.value
        val model = settings.value.model
        viewModelScope.launch {
            val existing = conversations.load(id)
            conversations.save(
                Conversation(
                    id = id,
                    title = existing?.title.orEmpty(),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    model = model,
                    messages = snapshot.map { msg ->
                        StoredMessage(
                            id = msg.id,
                            role = msg.role.name.lowercase(),
                            content = msg.content,
                            time = msg.time,
                            reasoning = msg.reasoning,
                            images = msg.images,
                            notice = msg.notice,
                            agentHandle = msg.agentHandle,
                        )
                    },
                )
            )
        }
    }

    fun newConversation() {
        persistConversation()
        _activeConversationId.value = java.util.UUID.randomUUID().toString()
        _messages.value = emptyList()
        _tasks.value = emptyList()
        _sessionSpend.value = SessionSpend()
        clearImages()
        refreshContext()
    }

    fun openConversation(id: String) {
        persistConversation()
        viewModelScope.launch {
            val convo = conversations.load(id) ?: return@launch
            _activeConversationId.value = convo.id
            _messages.value = convo.messages.map { stored ->
                ChatMessage(
                    id = stored.id,
                    role = if (stored.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
                    content = stored.content,
                    time = stored.time,
                    reasoning = stored.reasoning,
                    images = stored.images,
                    notice = stored.notice,
                    agentHandle = stored.agentHandle,
                )
            }
            _tasks.value = emptyList()
            refreshContext()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversations.delete(id)
            if (id == _activeConversationId.value) newConversation()
        }
    }

    fun setCustomMcpServers(servers: Set<String>) {
        viewModelScope.launch { store.setCustomMcpServers(servers) }
    }

    fun setCustomRegistries(registries: Set<String>) {
        viewModelScope.launch { store.setCustomRegistries(registries) }
    }

    fun setKeepAwake(enabled: Boolean) {
        viewModelScope.launch { store.setKeepAwake(enabled) }
    }

    fun setAgentMode(mode: AgentMode) {
        viewModelScope.launch { store.setAgentMode(mode.id) }
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        viewModelScope.launch { store.setReasoningEffort(effort.id) }
    }

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
        persistConversation()
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

    /** Warm connectivity probe — silent, so app-open never shows an error. */
    fun ensureConnected() {
        if (_isConnected.value != null) return
        viewModelScope.launch {
            val cfg = settings.value
            if (Models.providerOf(cfg.model) == Provider.KIMI && cfg.token.isBlank()) return@launch
            val before = _error.value
            testConnection()
            _error.value = before // probe results never surface as snackbars
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
