package com.kimimobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kimimobile.BuildConfig
import com.kimimobile.data.Models
import com.kimimobile.data.Provider
import com.kimimobile.ui.ChatViewModel
import com.kimimobile.ui.components.SettingsRow
import com.kimimobile.ui.components.SettingsSection
import com.kimimobile.ui.components.SettingsSwitch
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsState()
    val contextState by viewModel.contextState.collectAsState()
    val isCompacting by viewModel.isCompacting.collectAsState()
    val discoveringProxy by viewModel.discoveringProxy.collectAsState()
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    var baseUrl by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var zenKey by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var showZenKey by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var maxTokensText by rememberSaveable { mutableStateOf("") }
    var autoCompact by rememberSaveable { mutableStateOf(true) }
    var thresholdPct by rememberSaveable { mutableIntStateOf(80) }
    var loaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (!loaded) {
            baseUrl = settings.baseUrl
            token = settings.token
            zenKey = settings.zenApiKey
            maxTokensText = settings.maxContextTokens.toString()
            autoCompact = settings.autoCompact
            thresholdPct = settings.compactThresholdPct
            loaded = true
        }
    }

    LaunchedEffect(token, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        viewModel.store.setToken(token)
    }
    LaunchedEffect(baseUrl, loaded) {
        if (!loaded || baseUrl == settings.baseUrl) return@LaunchedEffect
        delay(600)
        if (baseUrl.isNotBlank()) viewModel.store.setBaseUrl(baseUrl, manual = true)
    }
    LaunchedEffect(zenKey, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        viewModel.store.setZenApiKey(zenKey)
    }
    LaunchedEffect(maxTokensText, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        maxTokensText.toLongOrNull()?.let(viewModel::setMaxContextTokens)
    }
    LaunchedEffect(autoCompact, thresholdPct, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(300)
        viewModel.setAutoCompact(autoCompact)
        viewModel.setCompactThreshold(thresholdPct)
    }

    val currentModel = Models.byId(settings.model) ?: Models.default
    val signedIn = settings.token.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
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
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ---- Account ----
            SettingsSection(
                title = "Account",
                subtitle = if (signedIn) "Signed in to Kimi" else "Not signed in",
            ) {
                if (signedIn) {
                    SettingsRow(
                        title = "Sign out",
                        subtitle = "Clears your saved Kimi token",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = {
                            scope.launch {
                                viewModel.store.clearToken()
                                token = ""
                                snackbarHostState.showSnackbar("Signed out")
                            }
                        },
                    )
                } else {
                    SettingsRow(
                        title = "Sign in with browser",
                        subtitle = "Recommended — grabs your token automatically",
                        icon = Icons.AutoMirrored.Filled.Login,
                        onClick = onOpenLogin,
                    )
                }
                SettingsRow(
                    title = "Test connection",
                    subtitle = testResult ?: "Checks your token against the server",
                    icon = Icons.Default.Check,
                    onClick = {
                        if (!testing) {
                            testing = true
                            testResult = null
                            scope.launch {
                                val ok = viewModel.testConnection(baseUrl, token, settings.model)
                                testing = false
                                testResult = if (ok) "Connected" else "Failed — check token and URL"
                            }
                        }
                    },
                    trailing = {
                        if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    },
                )
            }

            // ---- Model ----
            SettingsSection(
                title = "Model",
                subtitle = "${currentModel.name} · ${
                    String.format(Locale.US, "%,d", currentModel.contextTokens)
                } tokens",
            ) {
                Models.selectable(hasZenKey = settings.zenApiKey.isNotBlank()).forEach { model ->
                    SettingsRow(
                        title = model.name,
                        subtitle = model.description,
                        icon = if (model.provider == Provider.ZEN) Icons.Default.AutoAwesome else null,
                        onClick = { viewModel.setModel(model.id) },
                        trailing = {
                            if (model.id == settings.model) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }

            // ---- Providers ----
            SettingsSection(
                title = "Providers",
                subtitle = "Kimi runs through a local proxy; Zen's free models need nothing",
            ) {
                SettingsRow(
                    title = "Kimi proxy",
                    subtitle = when {
                        discoveringProxy -> "Searching…"
                        settings.baseUrl.isBlank() -> "Not found — tap to search again"
                        else -> settings.baseUrl
                    },
                    icon = Icons.Default.Dns,
                    onClick = { viewModel.rediscoverProxy() },
                    trailing = {
                        if (discoveringProxy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    },
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Kimi refresh token") },
                        placeholder = { Text("eyJhbGciOi…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (showToken) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showToken) "Hide" else "Show",
                                )
                            }
                        },
                        supportingText = { Text("Filled in automatically when you sign in") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = zenKey,
                        onValueChange = { zenKey = it },
                        label = { Text("OpenCode Zen API key (optional)") },
                        placeholder = { Text("Unlocks K3, Claude, GPT, Gemini…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (showZenKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showZenKey = !showZenKey }) {
                                Icon(
                                    if (showZenKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showZenKey) "Hide" else "Show",
                                )
                            }
                        },
                        supportingText = { Text("Free Zen models work without a key") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsSwitch(
                    title = "Keep runs awake",
                    subtitle = "Holds a wakelock during agent runs, cancellable from the notification",
                    icon = Icons.Default.Bolt,
                    checked = settings.keepAwake,
                    onCheckedChange = viewModel::setKeepAwake,
                )
                SettingsRow(
                    title = "Advanced",
                    subtitle = if (showAdvanced) "Hide proxy URL" else "Set the proxy URL manually",
                    icon = Icons.Default.Tune,
                    onClick = { showAdvanced = !showAdvanced },
                )
                if (showAdvanced) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Proxy URL override") },
                            placeholder = { Text("http://127.0.0.1:8000/v1") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            supportingText = { Text("Leave empty to keep auto-detecting") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- Context ----
            SettingsSection(
                title = "Context",
                subtitle = String.format(
                    Locale.US,
                    "%d%% used · %,d / %,d tokens",
                    (contextState.pct * 100).toInt(),
                    contextState.tokens,
                    contextState.maxTokens,
                ),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = maxTokensText,
                        onValueChange = { new -> maxTokensText = new.filter { it.isDigit() }.take(9) },
                        label = { Text("Max context tokens") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Set from the model; lower it if you hit limits sooner") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsSwitch(
                    title = "Auto-compact",
                    subtitle = "Summarize old turns when the window fills up",
                    icon = Icons.Default.Memory,
                    checked = autoCompact,
                    onCheckedChange = { autoCompact = it },
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Compact at",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
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
                }
                SettingsRow(
                    title = if (isCompacting) "Compacting…" else "Compact now",
                    subtitle = "Summarize this conversation immediately",
                    onClick = { if (!isCompacting) viewModel.compactNow() },
                    trailing = {
                        if (isCompacting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    },
                )
            }

            // ---- Agent ----
            SettingsSection(
                title = "Agent & tools",
                subtitle = "Tools, subagents and the marketplace",
            ) {
                SettingsSwitch(
                    title = "Agent mode",
                    subtitle = "Model calls tools and delegates to subagents",
                    icon = Icons.Default.Science,
                    checked = settings.agentEnabled,
                    onCheckedChange = viewModel::setAgentEnabled,
                )
                SettingsRow(
                    title = "Marketplace",
                    subtitle = "${settings.installedSkills.size} tools enabled",
                    icon = Icons.Default.Storefront,
                    onClick = onOpenMarketplace,
                )
            }

            // ---- Updates ----
            SettingsSection(title = "Updates") {
                SettingsRow(
                    title = "Check for updates",
                    subtitle = "${settings.updateChannel.lowercase()
                        .replaceFirstChar { it.uppercase() }} channel · v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onOpenUpdates,
                )
            }

            // ---- Conversation ----
            SettingsSection(title = "Conversation") {
                SettingsRow(
                    title = "Clear conversation",
                    subtitle = "Deletes all messages in this chat",
                    icon = Icons.Default.DeleteOutline,
                    onClick = { viewModel.clear() },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
