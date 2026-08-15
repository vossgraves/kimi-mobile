package com.kimimobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kimimobile.data.ApiMessage
import com.kimimobile.data.AppSettings
import com.kimimobile.data.ChatApi
import com.kimimobile.data.Models
import com.kimimobile.data.SettingsStore
import com.kimimobile.data.SkillEngine
import com.kimimobile.data.StreamChunk
import com.kimimobile.ui.components.AgentTask
import com.kimimobile.ui.components.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
        if (cfg.token.isBlank()) {
            _error.value = "Sign in or paste your refresh token in Settings first"
            return
        }
        if (cfg.agentEnabled) {
            sendAgent(text, cfg)
        } else {
            sendChat(text, cfg, images)
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

        val history = _messages.value
            .filter { it.id != placeholder.id && !it.failed }
            .map { ApiMessage(role = it.role.name.lowercase(), content = it.content, images = it.images) }

        _isStreaming.value = true
        _isConnected.value = null
        viewModelScope.launch {
            try {
                api.streamChat(cfg.baseUrl, cfg.token, resolvedModel(cfg), history).collect { chunkJson ->
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
                val history = mutableListOf<ApiMessage>()
                history += ApiMessage("system", agentSystemPrompt(cfg.installedSkills))
                history += _messages.value
                    .filter { it.id != placeholder.id && it.content.isNotBlank() }
                    .map { ApiMessage(role = it.role.name.lowercase(), content = it.content) }

                loop@ for (step in 1..MAX_AGENT_STEPS) {
                    val resp = api.complete(cfg.baseUrl, cfg.token, model, history)
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
                t.startsWith("PLAN:") || t.startsWith("STEP_DONE:") || t.startsWith("TOOL_CALL:")
            }
            .joinToString("\n")
            .trim()

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
            append("\n\nAfter each tool runs, a TOOL_RESULT line will appear — use it and continue. ")
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
        if (token.isBlank()) {
            _error.value = "Sign in first — no refresh token yet"
            return false
        }
        return try {
            val models = api.listModels(baseUrl, token)
            _error.value = if (models.isEmpty()) "Connected, but no models listed" else null
            _isConnected.value = true
            true
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
}
