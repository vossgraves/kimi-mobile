package com.kimi3.client.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kimi3.client.ui.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenMarketplace: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsState()
    val contextState by viewModel.contextState.collectAsState()
    val isCompacting by viewModel.isCompacting.collectAsState()

    var baseUrl by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var maxTokensText by rememberSaveable { mutableStateOf("") }
    var autoCompact by rememberSaveable { mutableStateOf(true) }
    var thresholdPct by rememberSaveable { mutableStateOf(80) }

    // Load persisted settings once.
    LaunchedEffect(Unit) {
        baseUrl = settings.baseUrl
        token = settings.token
        model = settings.model
        maxTokensText = settings.maxContextTokens.toString()
        autoCompact = settings.autoCompact
        thresholdPct = settings.compactThresholdPct
    }

    // Debounced autosave.
    LaunchedEffect(baseUrl, model) {
        delay(400)
        viewModel.store.save(baseUrl, token, model)
    }
    LaunchedEffect(token) {
        delay(600)
        viewModel.store.save(baseUrl, token, model)
    }
    LaunchedEffect(maxTokensText) {
        delay(500)
        maxTokensText.toLongOrNull()?.let(viewModel::setMaxContextTokens)
    }
    LaunchedEffect(autoCompact) {
        delay(400)
        viewModel.setAutoCompact(autoCompact)
    }
    LaunchedEffect(thresholdPct) {
        delay(400)
        viewModel.setCompactThreshold(thresholdPct)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Connection",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API base URL") },
                placeholder = { Text("http://10.0.2.2:8000/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                placeholder = { Text("kimi-k3") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Refresh token") },
                placeholder = { Text("eyJhbGciOi…") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showToken) "Hide token" else "Show token",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val ok = viewModel.testConnection(baseUrl, token, model)
                            testing = false
                            testResult = if (ok) "Connected — model responds" else "Connection failed"
                        }
                    },
                    enabled = !testing,
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            Modifier.width(16.dp).height(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Testing…")
                    } else {
                        Text("Test connection")
                    }
                }
                testResult?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Account",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            if (settings.token.isBlank()) {
                Button(
                    onClick = onOpenLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with browser")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.store.clearToken()
                            token = ""
                            snackbarHostState.showSnackbar("Signed out — token cleared")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Context window",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = String.format(
                    java.util.Locale.US,
                    "Estimated usage: %d%% (%,d / %,d tokens)",
                    (contextState.pct * 100).toInt(),
                    contextState.tokens,
                    contextState.maxTokens,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { maxTokensText = it.filter { c -> c.isDigit() } },
                label = { Text("Max context tokens") },
                placeholder = { Text("1048576") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-compact", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Summarize old turns when the window fills up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoCompact, onCheckedChange = { autoCompact = it })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compact at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "$thresholdPct%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = thresholdPct.toFloat(),
                onValueChange = { thresholdPct = it.toInt() },
                valueRange = 40f..95f,
                enabled = autoCompact,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { viewModel.compactNow() },
                enabled = !isCompacting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCompacting) {
                    CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Compacting…")
                } else {
                    Text("Compact conversation now")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Agent & tools",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agent mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Let the model call tools (search, fetch, math) to solve tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.agentEnabled,
                    onCheckedChange = viewModel::setAgentEnabled,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenMarketplace,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Browse marketplace")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Conversation",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.clear() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear conversation")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}