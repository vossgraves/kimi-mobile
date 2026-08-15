package com.kimimobile.data

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase

/**
 * User agents rot. A hardcoded "Chrome/124.0.0.0" is fine on day one and gets
 * you an "unsupported browser" wall a year later, because the string keeps
 * claiming a Chrome version that the world has moved past.
 *
 * So we don't hardcode one. The system WebView updates itself through the Play
 * Store, so its own default UA always names a current Chrome. We take that
 * string and only remove the bits that mark us as an embedded WebView:
 *
 *   - "; wv"  → the in-app WebView tag some sites use to block logins
 *   - "Version/4.0" → the legacy Android-browser token that ships with it
 *
 * Everything else (real Android version, real Chrome version, real device) is
 * left alone: it stays truthful, and it ages with the device instead of with
 * this source file.
 */
object WebViewUa {

    /** Cached because getDefaultUserAgent() spins up WebView machinery. */
    @Volatile
    private var cached: String? = null

    fun desktopClassMobile(context: Context): String {
        cached?.let { return it }
        val resolved = runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.let(::stripWebViewMarkers)
            ?.takeIf { it.contains("Chrome/") }
            ?: fallback()
        cached = resolved
        return resolved
    }

    internal fun stripWebViewMarkers(ua: String): String =
        ua.replace("; wv", "")
            .replace("Version/4.0 ", "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    /**
     * Only used if the platform call fails (no WebView provider installed).
     * Reports the real OS version rather than inventing one.
     */
    private fun fallback(): String =
        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
}

/**
 * Clears cookies and web storage before a sign-in run, so a half-finished or
 * stale session can't wedge the login page. Modelled on ArchiveTune's
 * WebAuthSessionCleaner.
 */
fun resetAuthWebViewSession(
    context: Context,
    webView: WebView,
    clearCookies: Boolean = true,
    onReady: () -> Unit,
) {
    webView.stopLoading()
    webView.clearHistory()
    webView.clearFormData()
    webView.clearCache(true)

    WebStorage.getInstance().deleteAllData()
    WebViewDatabase.getInstance(context.applicationContext).apply {
        clearFormData()
        clearHttpAuthUsernamePassword()
    }

    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(webView, true)

    if (!clearCookies) {
        onReady()
        return
    }
    cookies.removeSessionCookies {
        cookies.removeAllCookies {
            cookies.flush()
            cookies.setAcceptCookie(true)
            cookies.setAcceptThirdPartyCookies(webView, true)
            onReady()
        }
    }
}
