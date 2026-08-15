package com.kimimobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads an APK with progress and hands it to the system installer.
 * The user still confirms the install — this only removes the browser trip.
 */
object AppUpdateInstaller {

    private const val UPDATE_DIR = "updates"
    private const val APK_NAME = "kimi-mobile-update.apk"
    private const val BUFFER = 64 * 1024
    private const val PROGRESS_INTERVAL_MS = 120L

    data class Progress(val downloaded: Long, val total: Long) {
        val fraction: Float?
            get() = total.takeIf { it > 0 }
                ?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        onProgress: (Progress) -> Unit,
    ): Result<Unit> = runCatching {
        require(url.isNotBlank()) { "No download URL for this build" }
        val apk = withContext(Dispatchers.IO) { download(context, url, onProgress) }
        withContext(Dispatchers.Main) { install(context, apk) }
    }

    private suspend fun download(
        context: Context,
        url: String,
        onProgress: (Progress) -> Unit,
    ): File {
        val dir = File(context.cacheDir, UPDATE_DIR).apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val target = File(dir, APK_NAME)

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "KimiMobile-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var downloaded = 0L
                    var lastTick = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= PROGRESS_INTERVAL_MS) {
                            emit(downloaded, total, onProgress)
                            lastTick = now
                        }
                    }
                    emit(downloaded, total, onProgress)
                }
            }
        }

        if (!target.exists() || target.length() < 1024) {
            throw IOException("Downloaded file looks corrupt")
        }
        return target
    }

    private suspend fun emit(downloaded: Long, total: Long, onProgress: (Progress) -> Unit) {
        withContext(Dispatchers.Main.immediate) { onProgress(Progress(downloaded, total)) }
    }

    private fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
