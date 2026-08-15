package com.kimimobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The official Model Context Protocol registry — the same source Claude and
 * other clients use, rather than a list I made up.
 *
 * Servers advertise either `remotes` (HTTPS endpoints we can actually call
 * from a phone) or `packages` (npm/pypi processes that need a desktop host).
 * Only remote servers are installable here; the rest are shown as
 * desktop-only so the UI doesn't promise something it can't deliver.
 */
data class McpServer(
    val name: String,
    val title: String,
    val description: String,
    val remoteUrl: String?,
    val repository: String?,
    val version: String,
) {
    /** Remote servers speak HTTPS, so this app can drive them directly. */
    val installable: Boolean get() = remoteUrl != null
    val shortName: String get() = title.ifBlank { name.substringAfterLast('/') }
}

object McpRegistry {

    /** Default registry; overridable so you can point at your own. */
    const val OFFICIAL = "https://registry.modelcontextprotocol.io"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(
        query: String,
        registryUrl: String = OFFICIAL,
        limit: Int = 40,
    ): Result<List<McpServer>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(registryUrl.trimEnd('/'))
                append("/v0/servers?limit=").append(limit)
                if (query.isNotBlank()) {
                    append("&search=").append(java.net.URLEncoder.encode(query.trim(), "UTF-8"))
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KimiMobile/1.0")
                .get()
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Registry returned HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }

            val servers = json.parseToJsonElement(body).jsonObject["servers"]?.jsonArray
                ?: error("Unexpected registry response")

            servers.mapNotNull { entry ->
                val server = entry.jsonObject["server"]?.jsonObject ?: return@mapNotNull null
                val name = server["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val remote = server["remotes"]?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject?.get("url")?.jsonPrimitive?.content
                McpServer(
                    name = name,
                    title = server["title"]?.jsonPrimitive?.content.orEmpty(),
                    description = server["description"]?.jsonPrimitive?.content.orEmpty(),
                    remoteUrl = remote,
                    repository = server["repository"]?.jsonObject
                        ?.get("url")?.jsonPrimitive?.content,
                    version = server["version"]?.jsonPrimitive?.content ?: "—",
                )
            }
                // Newest duplicates first, and callable servers above the rest.
                .distinctBy { it.name }
                .sortedByDescending { it.installable }
        }
    }
}
