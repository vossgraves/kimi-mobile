package com.kimi3.client.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kimi3.client.data.ApiMessage
import com.kimi3.client.data.ChatApi
import com.kimi3.client.data.SettingsStore
import com.kimi3.client.data.StreamChunk
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
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val store = SettingsStore(app)
    val settings: StateFlow<com.kimi3.client.data.AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.kimi3.client.data.AppSettings())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnected = MutableStateFlow<Boolean?>(null)
    val isConnected: StateFlow<Boolean?> = _isConnected.asStateFlow()

    private val api = ChatApi()
    private val json = Json { ignoreUnknownKeys = true }

    fun send(text: String) {
        val cfg = settings.value
        if (text.isBlank() || _isStreaming.value) return
        if (cfg.token.isBlank()) {
            _error.value = "Set your refresh token in Settings first"
            return
        }

        val userMsg = ChatMessage(role = MessageRole.USER, content = text.trim())
        val placeholder = ChatMessage(role = MessageRole.ASSISTANT, content = "", streaming = true)
        _messages.update { it + userMsg + placeholder }

        val history = _messages.value
            .filter { it.id != placeholder.id }
            .map { ApiMessage(role = it.role.name.lowercase(), content = it.content) }

        _isStreaming.value = true
        _isConnected.value = null
        viewModelScope.launch {
            try {
                api.streamChat(cfg.baseUrl, cfg.token, cfg.model, history).collect { chunkJson ->
                    val content = runCatching {
                        json.decodeFromString<StreamChunk>(chunkJson)
                            .choices.firstOrNull()?.delta?.content.orEmpty()
                    }.getOrDefault("")
                    if (content.isNotEmpty()) {
                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == placeholder.id) msg.copy(content = msg.content + content)
                                else msg
                            }
                        }
                    }
                }
                _messages.update { list ->
                    list.map { if (it.id == placeholder.id) it.copy(streaming = false) else it }
                }
                _isConnected.value = true
            } catch (e: Exception) {
                _messages.update { list ->
                    list.map {
                        if (it.id == placeholder.id) it.copy(streaming = false, failed = true, content = "")
                        else it
                    }
                }
                _error.value = e.message ?: "Request failed"
                _isConnected.value = false
            } finally {
                _isStreaming.value = false
            }
        }
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
    }

    fun clearError() {
        _error.value = null
    }
}
