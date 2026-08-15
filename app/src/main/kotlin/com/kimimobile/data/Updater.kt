package com.kimimobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Which release train to follow. */
enum class UpdateChannel(val label: String, val description: String) {
    STABLE("Stable", "Tested releases from the main branch"),
    NIGHTLY("Nightly", "Fresh builds from dev — new features first, rough edges included"),
}

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val sizeBytes: Long = 0,
    val channel: UpdateChannel,
)

/**
 * Checks GitHub for new builds. Stable reads Releases; nightly reads the most
 * recent successful run of the nightly workflow on dev.
 *
 * ETags are honoured so repeat checks cost nothing against the rate limit.
 */
object Updater {

    private const val REPO = "vossgraves/kimi-mobile"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases"
    private const val NIGHTLY_RUNS_URL =
        "https://api.github.com/repos/$REPO/actions/workflows/nightly.yml/runs" +
            "?branch=dev&status=success&per_page=1&exclude_pull_requests=true"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** tag -> etag, so unchanged responses come back as 304. */
    private val etags = mutableMapOf<String, String>()
    private val cache = mutableMapOf<String, String>()

    suspend fun fetchLatest(channel: UpdateChannel): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (channel) {
                    UpdateChannel.STABLE -> fetchStable()
                    UpdateChannel.NIGHTLY -> fetchNightly()
                }
            }
        }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "KimiMobile-Updater")
            .apply { etags[url]?.let { header("If-None-Match", it) } }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 304) return cache[url]
            if (!response.isSuccessful) {
                if (response.code == 403) throw IllegalStateException("GitHub rate limit reached — try again later")
                throw IllegalStateException("Update check failed: HTTP ${response.code}")
            }
            response.header("ETag")?.let { etags[url] = it }
            return response.body?.string()?.also { cache[url] = it }
        }
    }

    private fun fetchStable(): ReleaseInfo? {
        val body = get(RELEASES_URL) ?: return null
        val releases = JSONArray(body)
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("prerelease") || r.optBoolean("draft")) continue
            val apk = pickApk(r.optJSONArray("assets"))
            return ReleaseInfo(
                tag = r.optString("tag_name"),
                name = r.optString("name").ifBlank { r.optString("tag_name") },
                notes = r.optString("body"),
                publishedAt = r.optString("published_at"),
                htmlUrl = r.optString("html_url"),
                downloadUrl = apk?.first,
                sizeBytes = apk?.second ?: 0L,
                channel = UpdateChannel.STABLE,
            )
        }
        return null
    }

    private fun fetchNightly(): ReleaseInfo? {
        val body = get(NIGHTLY_RUNS_URL) ?: return null
        val runs = JSONObject(body).optJSONArray("workflow_runs") ?: return null
        if (runs.length() == 0) return null
        val run = runs.getJSONObject(0)
        val sha = run.optString("head_sha").take(7)
        val runId = run.optLong("id")
        return ReleaseInfo(
            tag = "nightly-$sha",
            name = "Nightly $sha",
            notes = run.optString("display_title").ifBlank { "Latest dev build" },
            publishedAt = run.optString("updated_at"),
            htmlUrl = run.optString("html_url"),
            // Artifact downloads need auth, so we hand users the run page.
            // Nightly releases published as a rolling GitHub release are used
            // when present (see fetchNightlyRelease).
            downloadUrl = fetchNightlyRelease() ?: null,
            channel = UpdateChannel.NIGHTLY,
        )
    }

    /** A rolling prerelease tagged `nightly` carries the installable APK. */
    private fun fetchNightlyRelease(): String? = runCatching {
        val body = get("$RELEASES_URL/tags/nightly") ?: return null
        pickApk(JSONObject(body).optJSONArray("assets"))?.first
    }.getOrNull()

    private fun pickApk(assets: JSONArray?): Pair<String, Long>? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url") to a.optLong("size")
            }
        }
        return null
    }

    /**
     * Semantic-ish comparison that tolerates v-prefixes and nightly tags.
     * Returns true when [remote] is newer than [local].
     */
    fun isNewer(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        if (remote.startsWith("nightly", ignoreCase = true)) {
            // Nightlies are compared by commit, not version — treat a
            // different sha as newer.
            return !remote.equals(local, ignoreCase = true)
        }
        val r = versionParts(remote)
        val l = versionParts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(v: String): List<Int> =
        v.trimStart('v', 'V')
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}
