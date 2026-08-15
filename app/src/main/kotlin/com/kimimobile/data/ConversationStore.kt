package com.kimimobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class StoredMessage(
    val id: String,
    val role: String,
    val content: String,
    val time: Long,
    val reasoning: String = "",
    val images: List<String> = emptyList(),
    val notice: Boolean = false,
    val agentHandle: String? = null,
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: String = "",
    val messages: List<StoredMessage> = emptyList(),
)

/** Lightweight row for the sidebar — avoids loading every message. */
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
)

/**
 * Conversation history on disk. One JSON file per chat under files/chats,
 * which keeps saves cheap and means a corrupt chat can't take the rest down.
 */
class ConversationStore(context: Context) {

    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val summaries = dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    val convo = json.decodeFromString<Conversation>(file.readText())
                    ConversationSummary(
                        id = convo.id,
                        title = convo.title,
                        updatedAt = convo.updatedAt,
                        messageCount = convo.messages.size,
                    )
                }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
        _conversations.value = summaries
    }

    suspend fun load(id: String): Conversation? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<Conversation>(file.readText()) }.getOrNull()
    }

    suspend fun save(conversation: Conversation) = withContext(Dispatchers.IO) {
        // Don't persist empty chats — they'd pile up as "New chat" noise.
        if (conversation.messages.isEmpty()) return@withContext
        val stamped = conversation.copy(
            updatedAt = System.currentTimeMillis(),
            title = conversation.title.ifBlank { deriveTitle(conversation) },
        )
        runCatching {
            File(dir, "${stamped.id}.json").writeText(json.encodeToString(stamped))
        }
        refresh()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        refresh()
    }

    suspend fun rename(id: String, title: String) {
        val existing = load(id) ?: return
        save(existing.copy(title = title.trim().ifBlank { existing.title }))
    }

    /** First user message, trimmed to something that fits a sidebar row. */
    private fun deriveTitle(conversation: Conversation): String {
        val first = conversation.messages.firstOrNull { it.role == "user" }?.content.orEmpty()
        val cleaned = first.replace(Regex("\\s+"), " ").trim()
        return when {
            cleaned.isBlank() -> "New chat"
            cleaned.length <= 42 -> cleaned
            else -> cleaned.take(42).substringBeforeLast(' ', cleaned.take(42)) + "…"
        }
    }
}
