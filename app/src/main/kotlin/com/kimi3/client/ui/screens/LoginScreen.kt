package com.kimi3.client.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kimi3.client.data.SettingsStore
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * WebView-based login: loads kimi.com, lets the user sign in (SMS/OTP),
 * then auto-extracts the refresh_token from the page's localStorage.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    store: SettingsStore,
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var grabbing by remember { mutableStateOf(false) }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onToken(json: String) {
                Handler(Looper.getMainLooper()).post {
                    grabbing = true
                    scope.launch {
                        val obj = JSONObject(json)
                        val refresh = obj.optString("refresh_token")
                        if (refresh.isNotBlank()) {
                            store.setToken(refresh)
                            onLoggedIn()
                        }
                        grabbing = false
                    }
                }
            }
        }
    }

    // Polls localStorage for the auth tokens once the SPA is up.
    // Survives client-side route changes (pushState doesn't reload the document).
    val grabScript = """
        (function(){
          var done = false;
          var poll = function(){
            if (done) return;
            try {
              var rt = localStorage.getItem('refresh_token');
              var at = localStorage.getItem('access_token');
              if (rt) {
                done = true;
                Android.onToken(JSON.stringify({refresh_token: rt, access_token: at || ''}));
              }
            } catch(e) {}
          };
          poll();
          setInterval(poll, 1500);
        })();
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Kimi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (grabbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = { /* webview reload handled below */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        addJavascriptInterface(bridge, "Android")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                view?.evaluateJavascript(grabScript, null)
                            }
                        }
                        loadUrl("https://www.kimi.com")
                    }
                },
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
