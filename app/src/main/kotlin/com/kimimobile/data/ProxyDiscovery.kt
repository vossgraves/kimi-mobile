package com.kimimobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Finds the Kimi proxy so the user never has to type a URL.
 *
 * Tries the addresses a proxy realistically lives at — the emulator host, the
 * phone itself (Termux), and common LAN gateways — and keeps the first one
 * that answers /ping with "pong".
 */
object ProxyDiscovery {

    /** Ordered by how likely they are, cheapest first. */
    private val CANDIDATES = listOf(
        "http://127.0.0.1:8000/v1",   // proxy running on the phone (Termux)
        "http://10.0.2.2:8000/v1",    // emulator's view of the host machine
        "http://localhost:8000/v1",
        "http://192.168.1.100:8000/v1",
        "http://192.168.0.100:8000/v1",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /** Returns the first reachable proxy, or null if none answer. */
    suspend fun discover(extra: String? = null): String? = withContext(Dispatchers.IO) {
        val targets = buildList {
            extra?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(CANDIDATES)
        }.distinct()

        // Probe in parallel: a dead address costs a 2s timeout each otherwise.
        coroutineScope {
            targets.map { base ->
                async { if (ping(base)) base else null }
            }.awaitAll()
        }.filterNotNull().firstOrNull()
    }

    private fun ping(baseUrl: String): Boolean = runCatching {
        val root = baseUrl.trimEnd('/').removeSuffix("/v1")
        val request = Request.Builder().url("$root/ping").get().build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful && response.body?.string()?.contains("pong") == true
        }
    }.getOrDefault(false)
}
