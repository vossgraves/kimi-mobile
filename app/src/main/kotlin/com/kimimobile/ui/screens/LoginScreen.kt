package com.kimimobile.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kimimobile.data.SettingsStore
import com.kimimobile.data.WebViewUa
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val KIMI_URL = "https://www.kimi.com"

/**
 * WebView login: loads kimi.com, lets you sign in normally, then lifts the
 * refresh_token out of the page's localStorage.
 *
 * kimi.com is a JS-module SPA, which means it needs a WebChromeClient present
 * and its own storage intact — clearing cookies before load left a blank page.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    store: SettingsStore,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0) }
    var grabbing by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val handled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onToken(json: String) {
                if (!handled.compareAndSet(false, true)) return
                Handler(Looper.getMainLooper()).post {
                    grabbing = true
                    scope.launch {
                        val refresh = runCatching {
                            JSONObject(json).optString("refresh_token")
                        }.getOrDefault("")
                        if (refresh.isNotBlank()) {
                            store.setToken(refresh)
                            grabbing = false
                            onLoggedIn()
                        } else {
                            handled.set(false)
                            grabbing = false
                        }
                    }
                }
            }
        }
    }

    // Polls localStorage for the token. Runs on every page, survives SPA route
    // changes, and stops itself once it finds one.
    val grabScript = """
        (function(){
          if (window.__kimiGrab) return;
          window.__kimiGrab = true;
          var tries = 0;
          var id = setInterval(function(){
            tries++;
            try {
              var rt = localStorage.getItem('refresh_token');
              if (rt && rt.length > 20) {
                clearInterval(id);
                Android.onToken(JSON.stringify({refresh_token: rt}));
              } else if (tries > 900) {
                clearInterval(id);
              }
            } catch(e) {}
          }, 1000);
        })();
    """.trimIndent()

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeJavascriptInterface("Android")
                destroy()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Kimi") },
                navigationIcon = {
                    IconButton(onClick = { if (canGoBack) webView?.goBack() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (grabbing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = {
                        loadError = null
                        webView?.reload()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // Derived from the installed WebView, minus the
                            // "; wv" tag that gets SPAs to serve a blank shell.
                            userAgentString = WebViewUa.desktopClassMobile(ctx)
                        }
                        // `this` here is the WebView; using apply on the
                        // CookieManager would pass the wrong receiver.
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        addJavascriptInterface(bridge, "Android")

                        // Module scripts and OAuth popups need a chrome client;
                        // without one kimi.com renders an empty page.
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                canGoBack = view?.canGoBack() == true
                                view?.evaluateJavascript(grabScript, null)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                // Only surface failures of the main document.
                                if (request?.isForMainFrame == true) {
                                    loadError = "Couldn't reach kimi.com — check your connection."
                                }
                            }
                        }
                        webView = this
                        loadUrl(KIMI_URL)
                    }
                },
            )

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }

            loadError?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        loadError = null
                        webView?.loadUrl(KIMI_URL)
                    }) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}
