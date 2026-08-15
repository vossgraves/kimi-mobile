package com.kimi3.client.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ApiMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
)

@Serializable
data class StreamChunk(
    val choices: List<StreamChoice> = emptyList(),
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta? = null,
    val finish_reason: String? = null,
)

@Serializable
data class StreamDelta(
    val content: String? = null,
    val reasoning_content: String? = null,
)

@Serializable
data class ErrorResponse(val error: ApiError? = null)

@Serializable
data class ApiError(val message: String? = null)

@Serializable
data class ModelListResponse(val data: List<ModelInfo> = emptyList())

@Serializable
data class ModelInfo(val id: String)

private val JSON = Json { ignoreUnknownKeys = true }

/** Minimal OpenAI-compatible streaming chat client, hand-rolled SSE (no extra deps). */
class ChatApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    /** Emits raw SSE `data:` payloads (JSON chunk objects), completes on [DONE]. */
    fun streamChat(
        baseUrl: String,
        token: String,
        model: String,
        messages: List<ApiMessage>,
    ): Flow<String> = callbackFlow {
        val body = JSON.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model = model, messages = messages)
        ).toRequestBody("application/json".toMediaType())

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
                        val bodyText = it.body?.string().orEmpty()
                        val msg = runCatching {
                            JSON.decodeFromString<ErrorResponse>(bodyText).error?.message
                        }.getOrNull()
                        close(IOException("HTTP ${it.code}: ${msg ?: bodyText.take(200)}"))
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

    /** Quick connectivity check: list available models. Returns the model ids. */
    suspend fun listModels(baseUrl: String, token: String): List<String> {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                throw IOException("HTTP ${response.code}: $bodyText")
            }
            val parsed = JSON.decodeFromString<ModelListResponse>(response.body!!.string())
            return parsed.data.map { it.id }
        }
    }
}
