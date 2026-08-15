package com.kimimobile.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kimimobile.data.SettingsStore
import com.kimimobile.data.WebViewUa
import com.kimimobile.data.resetAuthWebViewSession
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * WebView login: loads kimi.com, lets the user sign in (SMS/OTP or OAuth),
 * then lifts the refresh_token straight out of the page's localStorage.
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
    var loading by remember { mutableStateOf(true) }
    var grabbing by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    // Guards against the JS poll firing twice before navigation happens.
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
                            // Persist BEFORE navigating so the chat screen
                            // never reads a stale empty token.
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

    // Polls localStorage for the token once the SPA is up. Survives client-side
    // route changes (pushState doesn't reload the document). The interval is
    // cleared once we have a token so it can't outlive the login.
    val grabScript = """
        (function(){
          if (window.__kimiGrab) return;
          window.__kimiGrab = true;
          var tries = 0;
          var id = setInterval(function(){
            tries++;
            try {
              var rt = localStorage.getItem('refresh_token');
              if (rt) {
                clearInterval(id);
                Android.onToken(JSON.stringify({refresh_token: rt}));
              } else if (tries > 600) {
                clearInterval(id);
              }
            } catch(e) { clearInterval(id); }
          }, 1000);
        })();
    """.trimIndent()

    // In-page back before leaving the screen.
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

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
                    IconButton(onClick = { webView?.reload() }) {
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
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.databaseEnabled = true
                        // Derived from the installed WebView so it can't go stale.
                        settings.userAgentString = WebViewUa.desktopClassMobile(ctx)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        addJavascriptInterface(bridge, "Android")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                canGoBack = view?.canGoBack() == true
                                view?.evaluateJavascript(grabScript, null)
                            }
                        }
                        webView = this
                        // Clear any half-finished session before starting.
                        resetAuthWebViewSession(ctx, this) {
                            loadUrl("https://www.kimi.com")
                        }
                    }
                },
            )
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
