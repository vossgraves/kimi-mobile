package com.kimimobile.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the Termux/Debian sidecar.
 *
 * Android forbids an app from spawning processes — since API 29, W^X blocks
 * executing anything out of app-writable storage, which is why a bundled
 * proot-Debian can't work here (Termux only manages it by targeting API 28).
 * So the things that need a real process — stdio MCP servers, npx, git,
 * shell — run in the container that's already on the phone, and the app
 * reaches them over localhost. Same arrangement as the Kimi proxy.
 *
 * Everything degrades gracefully: with no sidecar the app is exactly what it
 * was, just without process-backed tools.
 */
object SidecarClient {

    private const val DEFAULT_URL = "http://127.0.0.1:8777"

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)   // localhost: fail fast when absent
        .readTimeout(120, TimeUnit.SECONDS)    // npx cold start can be slow
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    data class Health(
        val available: Boolean,
        val node: String = "",
        val skillCount: Int = 0,
        val mcpCount: Int = 0,
    )

    data class Skill(val id: String, val name: String, val description: String)

    data class McpTool(val server: String, val name: String, val description: String)

    @Volatile
    private var baseUrl: String = DEFAULT_URL

    fun configure(url: String) {
        baseUrl = url.trimEnd('/').ifBlank { DEFAULT_URL }
    }

    private suspend fun get(path: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl$path").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let { json.parseToJsonElement(it).jsonObject }
            }
        }.getOrNull()
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl$path")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    response.body?.string()?.let { json.parseToJsonElement(it).jsonObject }
                }
            }.getOrNull()
        }

    /** Cheap probe — the UI uses this to decide whether to offer any of it. */
    suspend fun health(): Health {
        val body = get("/health") ?: return Health(available = false)
        return Health(
            available = body["ok"]?.jsonPrimitive?.content == "true",
            node = body["node"]?.jsonPrimitive?.content.orEmpty(),
            skillCount = body["skills"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            mcpCount = body["mcpServers"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        )
    }

    /** SKILL.md files discovered in the usual agent directories. */
    suspend fun skills(): List<Skill> {
        val body = get("/skills") ?: return emptyList()
        return body["skills"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Skill(
                id = id,
                name = obj["name"]?.jsonPrimitive?.content ?: id,
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
            )
        }.orEmpty()
    }

    /** Installs a skill by repo or shorthand, e.g. "composiohq/skills". */
    suspend fun installSkill(source: String): Result<String> {
        val body = post("/skills/install", buildJsonObject { put("source", source) })
            ?: return Result.failure(IllegalStateException("Sidecar unreachable"))
        val ok = body["ok"]?.jsonPrimitive?.content == "true"
        val text = body["stderr"]?.jsonPrimitive?.content
            ?: body["stdout"]?.jsonPrimitive?.content.orEmpty()
        return if (ok) Result.success(text) else Result.failure(IllegalStateException(text))
    }

    /** The full SKILL.md body, to inject into the agent's system prompt. */
    suspend fun skillBody(id: String): String? =
        post("/skills/read", buildJsonObject { put("id", id) })
            ?.get("body")?.jsonPrimitive?.content

    /** Registers a stdio MCP server the sidecar will launch on demand. */
    suspend fun addMcpServer(name: String, command: String, args: List<String>): Boolean {
        val payload = buildJsonObject {
            put("name", name)
            put("command", command)
            put("args", buildJsonArray {
                args.forEach { add(JsonPrimitive(it)) }
            })
        }
        return post("/mcp/add", payload) != null
    }

    suspend fun mcpServers(): List<String> =
        get("/mcp")?.get("servers")?.jsonObject?.keys?.toList().orEmpty()

    suspend fun mcpTools(server: String): List<McpTool> {
        val body = post("/mcp/tools", buildJsonObject { put("name", server) })
            ?: return emptyList()
        return body["tools"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            McpTool(
                server = server,
                name = name,
                description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
            )
        }.orEmpty()
    }

    /** Runs an MCP tool and returns its text output. */
    suspend fun callMcp(server: String, tool: String, args: JsonObject): Result<String> {
        val payload = buildJsonObject {
            put("name", server)
            put("tool", tool)
            put("args", args)
        }
        val body = post("/mcp/call", payload)
            ?: return Result.failure(IllegalStateException("Sidecar unreachable"))
        body["error"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return Result.failure(IllegalStateException(it)) }
        return Result.success(body["text"]?.jsonPrimitive?.content.orEmpty())
    }

    /** Allowlisted shell — the sidecar refuses anything destructive. */
    suspend fun shell(command: String): Result<String> {
        val body = post("/shell", buildJsonObject { put("cmd", command) })
            ?: return Result.failure(IllegalStateException("Sidecar unreachable"))
        val ok = body["ok"]?.jsonPrimitive?.content == "true"
        val out = body["stdout"]?.jsonPrimitive?.content.orEmpty()
        val err = body["stderr"]?.jsonPrimitive?.content.orEmpty()
        return if (ok) Result.success(out.ifBlank { "(no output)" })
        else Result.failure(IllegalStateException(err.ifBlank { "Command failed" }))
    }

    /** The one-liner shown in Settings when no sidecar is running. */
    const val SETUP_COMMAND: String =
        "curl -sL https://raw.githubusercontent.com/vossgraves/kimi-mobile/main/sidecar/sidecar.js " +
            "-o ~/kimi-sidecar.js && node ~/kimi-sidecar.js"
}
