package com.kimimobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches the live model list instead of shipping a hardcoded one that rots.
 *
 *  - Kimi: the proxy's own /v1/models, so you only ever see what it can serve
 *    (that's why kimi-k3 never appeared — the proxy doesn't have it).
 *  - Zen: /zen/v1/models for availability, enriched from models.dev for
 *    context size, pricing and capabilities.
 *
 * Falls back to the built-in list when offline.
 */
object ModelCatalog {

    private const val MODELS_DEV = "https://models.dev/api.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Cached for the process lifetime; refresh() forces a re-fetch. */
    @Volatile
    private var cached: List<KimiModel>? = null

    suspend fun load(
        kimiBaseUrl: String,
        kimiToken: String,
        zenKey: String,
        force: Boolean = false,
    ): List<KimiModel> {
        if (!force) cached?.let { return it }
        // Real context windows first — everything below resolves against it.
        ContextWindows.refresh()

        val fetched = withContext(Dispatchers.IO) {
            // Fetch both in parallel; a slow proxy shouldn't delay Zen.
            coroutineScope {
                val zenJob = async { runCatching { fetchZen(zenKey) }.getOrDefault(emptyList()) }
                val kimiJob = async {
                    runCatching { fetchKimi(kimiBaseUrl, kimiToken) }.getOrDefault(emptyList())
                }
                kimiJob.await() + zenJob.await()
            }
        }

        // Merge rather than replace: if one provider fails to answer we still
        // want the other's models, plus the built-ins as a floor. This is why
        // Zen models could vanish entirely — a single failure dropped them.
        val result = (fetched + Models.all)
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.provider.ordinal }, { it.requiresKey }, { it.name }))
        cached = result
        return result
    }

    fun cachedOrDefault(): List<KimiModel> = cached ?: Models.all

    // ---- Kimi (via the proxy) ------------------------------------------------

    private fun fetchKimi(baseUrl: String, token: String): List<KimiModel> {
        if (baseUrl.isBlank()) return emptyList()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models")
            .header("Authorization", "Bearer ${token.ifBlank { "anonymous" }}")
            .get()
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptyList()
        // The proxy's metadata predates K3, but kimi.com itself runs K3 and
        // answers to the name (verified live) — so it's always offered first.
        val k3 = Models.byId("kimi-k3")
        val listed = data.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val known = Models.byId(id)
            val isVision = id.contains("vision") || id == "kimi-latest"
            val stated = obj["description"]?.jsonPrimitive?.content.orEmpty()
            KimiModel(
                id = id,
                name = known?.name ?: obj["name"]?.jsonPrimitive?.content ?: id,
                description = known?.description ?: stated,
                // models.dev, else the size the proxy states in its own
                // description ("256k上下文"), else the built-in value.
                contextTokens = ContextWindows.resolve(
                    id = id,
                    description = stated,
                    fallback = known?.contextTokens ?: contextFromId(id),
                ),
                provider = Provider.KIMI,
                vision = isVision,
                reasoning = id.contains("thinking"),
                hidden = isVision,
            )
        }
        return listOfNotNull(k3) + listed.filterNot { it.id == "kimi-k3" }
    }

    /** Kimi ids encode their window: moonshot-v1-32k, k2 = 256k. */
    private fun contextFromId(id: String): Long = when {
        id.contains("128k") -> 131_072
        id.contains("32k") -> 32_768
        id.contains("8k") -> 8_192
        id.startsWith("kimi-k2") -> 262_144
        else -> 131_072
    }

    // ---- Zen (+ models.dev metadata) ----------------------------------------

    private fun fetchZen(zenKey: String): List<KimiModel> {
        val available = zenModelIds() ?: return emptyList()
        val meta = runCatching { modelsDevMetadata() }.getOrDefault(emptyMap())

        return available.map { id ->
            val info = meta[id]
            val free = id.endsWith("-free") || info?.free == true
            KimiModel(
                id = id,
                name = info?.name ?: prettify(id),
                description = buildString {
                    append(if (free) "Free" else "Paid")
                    info?.description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                contextTokens = ContextWindows.resolve(
                    id = id,
                    fallback = info?.context ?: 131_072,
                ),
                provider = Provider.ZEN,
                vision = info?.vision == true,
                reasoning = info?.reasoning == true,
                // Paid Zen models are only usable once a key is present.
                requiresKey = !free,
                hidden = false,
            )
        }.sortedWith(compareBy({ it.requiresKey }, { it.name }))
    }

    private fun zenModelIds(): List<String>? {
        // Same client-identity gate as chat requests — see ChatApi.
        val request = Request.Builder()
            .url("${Models.ZEN_BASE_URL}/models")
            .header("User-Agent", "opencode/1.0.0")
            .get()
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return null
        return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
    }

    private data class ModelMeta(
        val name: String,
        val description: String,
        val context: Long,
        val reasoning: Boolean,
        val vision: Boolean,
        val free: Boolean,
    )

    private fun modelsDevMetadata(): Map<String, ModelMeta> {
        val request = Request.Builder().url(MODELS_DEV).get().build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            response.body?.string().orEmpty()
        }
        val models = json.parseToJsonElement(body)
            .jsonObject["opencode"]?.jsonObject
            ?.get("models")?.jsonObject
            ?: return emptyMap()

        return models.mapValues { (_, value) ->
            val obj = value.jsonObject
            val cost = obj["cost"]?.jsonObject
            val inputCost = cost?.get("input")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val outputCost = cost?.get("output")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val modalities = obj["modalities"]?.jsonObject
                ?.get("input")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content }
                .orEmpty()
            ModelMeta(
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                context = obj["limit"]?.jsonObject?.get("context")
                    ?.jsonPrimitive?.content?.toLongOrNull() ?: 131_072,
                reasoning = obj["reasoning"]?.jsonPrimitive?.content == "true",
                vision = "image" in modalities,
                free = inputCost == 0.0 && outputCost == 0.0,
            )
        }
    }

    private fun prettify(id: String): String =
        id.removeSuffix("-free")
            .split('-', '.')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}
