package com.kimimobile.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException

/**
 * A message part. Kimi's web API accepts either a plain string content or an
 * array of parts — `text` and `image_url` (base64 data URLs work, verified).
 */
@Serializable
data class ApiMessage(
    val role: String,
    val content: String,
    val images: List<String> = emptyList(),
)

@Serializable
data class StreamChunk(
    val choices: List<StreamChoice> = emptyList(),
    val usage: UsageInfo? = null,
    val cost: String? = null,
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class StreamDelta(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class ErrorResponse(val error: ApiError? = null, val message: String? = null, val code: Int? = null)

@Serializable
data class ApiError(val message: String? = null)

@Serializable
data class ModelListResponse(val data: List<ModelInfo> = emptyList())

@Serializable
data class ModelInfo(val id: String)

/** Usage/cost as reported per response — Zen sends both, Kimi fakes usage. */
data class SpendReport(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val costUsd: Double = 0.0,
)

@Serializable
private data class CompletionResponse(
    val choices: List<CompletionChoice> = emptyList(),
    val usage: UsageInfo? = null,
    val cost: String? = null,
)

@Serializable
data class UsageInfo(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
)

@Serializable
private data class CompletionChoice(val message: CompletionMessage? = null)

@Serializable
private data class CompletionMessage(val content: String? = null)

private val JSON = Json { ignoreUnknownKeys = true }

/** Minimal OpenAI-compatible client, hand-rolled SSE (no extra deps). */
class ChatApi(
    /** Invoked whenever a response reports usage, for the credit counter. */
    var onSpend: ((SpendReport) -> Unit)? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build(),
) {

    /** Builds the request body, using multi-part content when images are attached. */
    private fun buildBody(
        model: String,
        messages: List<ApiMessage>,
        stream: Boolean,
        maxTokens: Int?,
        effort: String? = null,
    ): String {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", stream)
            if (maxTokens != null) put("max_tokens", maxTokens)
            // Providers that don't know this parameter ignore it.
            if (effort != null) put("reasoning_effort", effort)
            put("messages", buildJsonArray {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        if (msg.images.isEmpty()) {
                            put("content", msg.content)
                        } else {
                            put("content", buildJsonArray {
                                msg.images.forEach { dataUrl ->
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject { put("url", dataUrl) })
                                    })
                                }
                                if (msg.content.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", msg.content)
                                    })
                                }
                            })
                        }
                    })
                }
            })
        }
        return payload.toString()
    }

    /** Emits raw SSE `data:` payloads (JSON chunk objects), completes on [DONE]. */
    fun streamChat(
        baseUrl: String,
        token: String,
        model: String,
        messages: List<ApiMessage>,
        effort: String? = null,
    ): Flow<String> = callbackFlow {
        val body = buildBody(model, messages, stream = true, maxTokens = null, effort = effort)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        close(IOException(friendlyError(it.code, it.body?.string().orEmpty())))
                        return
                    }
                    val source = it.body?.source() ?: run {
                        close(IOException("Empty response body"))
                        return
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data:")) {
                                val data = line.removePrefix("data:").trim()
                                if (data == "[DONE]") break
                                trySend(data)
                            }
                        }
                        close()
                    } catch (e: IOException) {
                        close(e)
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }

    /** Non-streaming completion — used by the agent loop and context compaction. */
    suspend fun complete(
        baseUrl: String,
        token: String,
        model: String,
        messages: List<ApiMessage>,
        maxTokens: Int = 4096,
    ): String {
        val body = buildBody(model, messages, stream = false, maxTokens = maxTokens)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
            val parsed = JSON.decodeFromString<CompletionResponse>(text)
            reportSpend(parsed.usage, parsed.cost)
            return parsed.choices.firstOrNull()?.message?.content.orEmpty()
        }
    }

    /**
     * Validates a Kimi refresh token via the proxy's /token/check endpoint —
     * /models answers even for a dead token, so it can't prove sign-in.
     */
    suspend fun checkToken(baseUrl: String, token: String): Boolean {
        // /token/check sits at the server root, not under /v1.
        val root = baseUrl.trimEnd('/').removeSuffix("/v1")
        val payload = buildJsonObject { put("token", token) }.toString()
        val request = Request.Builder()
            .url("$root/token/check")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
            return text.contains("\"live\":true")
        }
    }

    /** Quick connectivity check: list available models. Returns the model ids. */
    suspend fun listModels(baseUrl: String, token: String): List<String> {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
            return JSON.decodeFromString<ModelListResponse>(text).data.map { it.id }
        }
    }

    private fun reportSpend(usage: UsageInfo?, cost: String?) {
        val callback = onSpend ?: return
        if (usage == null && cost == null) return
        callback(
            SpendReport(
                promptTokens = usage?.promptTokens ?: 0,
                completionTokens = usage?.completionTokens ?: 0,
                costUsd = cost?.toDoubleOrNull() ?: 0.0,
            )
        )
    }

    /** Turns proxy/upstream errors into something a human can act on. */
    private fun friendlyError(code: Int, body: String): String {
        val parsed = runCatching { JSON.decodeFromString<ErrorResponse>(body) }.getOrNull()
        val raw = parsed?.error?.message ?: parsed?.message ?: body.take(200)
        return when {
            parsed?.code == -2001 || raw.contains("请求失败") ->
                "Kimi rejected the request — this model or feature may not be available on the free tier"
            parsed?.code == -2003 || raw.contains("is not valid") ->
                "Attachment rejected: $raw"
            code == 401 || raw.contains("token", true) && code >= 400 ->
                "Token expired or invalid — sign in again in Settings"
            code == 429 ->
                "Rate limited — you've hit the free-tier cap, try again later"
            else -> "HTTP $code: $raw"
        }
    }
}
