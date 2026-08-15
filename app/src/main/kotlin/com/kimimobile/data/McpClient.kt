package com.kimimobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal MCP client over streamable HTTP. Enough to list a remote server's
 * tools and call them, which is what turns a "connector" into something the
 * agent can genuinely use from a phone.
 *
 * stdio servers (npm/pypi packages) are out of scope — Android can't spawn
 * processes, so those stay desktop-only.
 */
class McpClient(
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap(),
) {

    data class Tool(val name: String, val description: String, val schema: JsonObject?)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val ids = AtomicInteger(0)

    /** Set after initialize(); some servers require it on later calls. */
    private var sessionId: String? = null

    private suspend fun rpc(method: String, params: JsonObject? = null): JsonObject =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", ids.incrementAndGet())
                put("method", method)
                if (params != null) put("params", params)
            }

            val builder = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                // Streamable HTTP servers may answer as JSON or as SSE.
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            headers.forEach { (k, v) -> builder.header(k, v) }
            sessionId?.let { builder.header("Mcp-Session-Id", it) }

            client.newCall(builder.build()).execute().use { response ->
                response.header("Mcp-Session-Id")?.let { sessionId = it }
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("MCP ${response.code}: ${text.take(160)}")
                }
                val payloadText = if (text.startsWith("event:") || text.contains("\ndata:")) {
                    // SSE framing: take the first data line.
                    text.lineSequence()
                        .firstOrNull { it.startsWith("data:") }
                        ?.removePrefix("data:")
                        ?.trim()
                        .orEmpty()
                } else {
                    text
                }
                val obj = json.parseToJsonElement(payloadText).jsonObject
                obj["error"]?.jsonObject?.let { err ->
                    error(err["message"]?.jsonPrimitive?.content ?: "MCP error")
                }
                obj["result"]?.jsonObject ?: buildJsonObject { }
            }
        }

    suspend fun initialize(): Result<Unit> = runCatching {
        rpc(
            "initialize",
            buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                put("capabilities", buildJsonObject { })
                put("clientInfo", buildJsonObject {
                    put("name", "kimi-mobile")
                    put("version", "1.0")
                })
            },
        )
        Unit
    }

    suspend fun listTools(): Result<List<Tool>> = runCatching {
        val result = rpc("tools/list")
        result["tools"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Tool(
                name = name,
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                schema = obj["inputSchema"]?.jsonObject,
            )
        }.orEmpty()
    }

    suspend fun callTool(name: String, arguments: JsonObject): Result<String> = runCatching {
        val result = rpc(
            "tools/call",
            buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
        )
        // Content is a list of typed blocks; concatenate the text ones.
        result["content"]?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("\n")
            ?.ifBlank { "(no textual output)" }
            ?: "(no output)"
    }

    companion object {
        private const val PROTOCOL_VERSION = "2025-06-18"
    }
}
