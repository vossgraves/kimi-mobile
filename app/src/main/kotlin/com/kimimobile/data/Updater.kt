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
    // Nightlies are published as dated prereleases (N202608151530-abc1234),
    // stable as v-tagged releases marked latest — same scheme ArchiveTune uses.
    private const val NIGHTLY_TAG_PREFIX = "N"

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
        val body = get(RELEASES_URL) ?: return null
        val releases = JSONArray(body)
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val tag = r.optString("tag_name")
            // Newest nightly prerelease wins; the list is already newest-first.
            if (!r.optBoolean("prerelease") || !tag.startsWith(NIGHTLY_TAG_PREFIX)) continue
            val apk = pickApk(r.optJSONArray("assets"))
            return ReleaseInfo(
                tag = tag,
                name = r.optString("name").ifBlank { tag },
                notes = r.optString("body"),
                publishedAt = r.optString("published_at"),
                htmlUrl = r.optString("html_url"),
                downloadUrl = apk?.first,
                sizeBytes = apk?.second ?: 0L,
                channel = UpdateChannel.NIGHTLY,
            )
        }
        return null
    }

    private fun pickApk(assets: JSONArray?): Pair<String, Long>? {
        if (assets == null) return null
        var fallback: Pair<String, Long>? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val entry = a.optString("browser_download_url") to a.optLong("size")
            // The minified release build is the one worth installing; only
            // fall back to debug if no release asset exists.
            if (name.contains("release", ignoreCase = true)) return entry
            if (fallback == null) fallback = entry
        }
        return fallback
    }

    /**
     * Semantic-ish comparison that tolerates v-prefixes and nightly tags.
     * Returns true when [remote] is newer than [local].
     */
    fun isNewer(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        if (remote.startsWith(NIGHTLY_TAG_PREFIX) && remote.length > 9) {
            // Nightly tags are N<yyyymmddHHMM>-<sha>: a different tag is a
            // different build, and they sort chronologically.
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
