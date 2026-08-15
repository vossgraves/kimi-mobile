package com.kimimobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Context windows, fetched rather than hardcoded.
 *
 * I got K3 wrong twice by typing numbers in by hand, so they now come from
 * models.dev — the same catalogue opencode itself reads — covering both the
 * `opencode` (Zen) and `moonshotai` (Kimi) providers. Resolution order:
 *
 *  1. models.dev entry for the exact id, under either provider
 *  2. the size stated in the model's own description or id ("256k上下文",
 *     "moonshot-v1-32k") — the Kimi proxy publishes this but no numeric field
 *  3. the built-in table, so the app still works offline
 */
object ContextWindows {

    private const val MODELS_DEV = "https://models.dev/api.json"
    private val PROVIDERS = listOf("opencode", "moonshotai", "anthropic", "google", "openai")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** id -> context tokens, populated on first fetch. */
    @Volatile
    private var fetched: Map<String, Long> = emptyMap()

    val isLoaded: Boolean get() = fetched.isNotEmpty()

    /** Pulls the catalogue once; safe to call repeatedly. */
    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (fetched.isNotEmpty() && !force) return@withContext true
        runCatching {
            val request = Request.Builder()
                .url(MODELS_DEV)
                .header("User-Agent", "opencode/1.0.0")
                .get()
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                response.body?.string().orEmpty()
            }
            val root = json.parseToJsonElement(body).jsonObject
            val out = mutableMapOf<String, Long>()
            PROVIDERS.forEach { provider ->
                val models = root[provider]?.jsonObject?.get("models")?.jsonObject ?: return@forEach
                models.forEach { (id, entry) ->
                    val ctx = entry.jsonObject["limit"]?.jsonObject
                        ?.get("context")?.jsonPrimitive?.content?.toLongOrNull()
                    // First provider wins; opencode is listed first because
                    // that's what Zen actually serves.
                    if (ctx != null && ctx > 0 && id !in out) out[id] = ctx
                }
            }
            fetched = out
            out.isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * Best known window for a model id, trying the live catalogue, then the
     * text the provider gave us, then the built-in default.
     */
    fun resolve(id: String, description: String = "", fallback: Long = 131_072L): Long {
        fetched[id]?.let { return it }
        // Suffixed ids (kimi-k3-search) still refer to the base model.
        fetched.entries
            .filter { id.startsWith("${it.key}-") }
            .maxByOrNull { it.key.length }
            ?.let { return it.value }
        parseStated(id)?.let { return it }
        parseStated(description)?.let { return it }
        return fallback
    }

    /**
     * Reads a window out of free text: "256k上下文", "128k context", "1M".
     * The Kimi proxy states sizes this way and nowhere else.
     */
    internal fun parseStated(text: String): Long? {
        if (text.isBlank()) return null
        val match = Regex("""(\d+(?:\.\d+)?)\s*([kKmM])""").find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val scaled = when (match.groupValues[2].lowercase()) {
            "k" -> value * 1024
            "m" -> value * 1024 * 1024
            else -> return null
        }.toLong()
        // Ignore matches that clearly aren't context sizes.
        return scaled.takeIf { it in 1_024..4_194_304 }
    }
}
