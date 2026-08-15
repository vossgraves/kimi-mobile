package com.kimimobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kimimobile.BuildConfig
import com.kimimobile.data.AgentMode
import com.kimimobile.data.Models
import com.kimimobile.ui.ChatViewModel
import com.kimimobile.ui.components.ClaudeDivider
import com.kimimobile.ui.components.ClaudeGroup
import com.kimimobile.ui.components.ClaudePill
import com.kimimobile.ui.components.ClaudeRow
import com.kimimobile.ui.components.ClaudeToggle
import com.kimimobile.ui.components.GroupLabel
import com.kimimobile.ui.theme.Claude
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

    var token by rememberSaveable { mutableStateOf("") }
    var zenKey by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var showZenKey by rememberSaveable { mutableStateOf(false) }
    var maxTokensText by rememberSaveable { mutableStateOf("") }
    var thresholdPct by rememberSaveable { mutableIntStateOf(80) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showKeys by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (!loaded && settings.loaded) {
            token = settings.token
            zenKey = settings.zenApiKey
            baseUrl = settings.baseUrl
            maxTokensText = settings.maxContextTokens.toString()
            thresholdPct = settings.compactThresholdPct
            loaded = true
        }
    }
    LaunchedEffect(token, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        if (token != settings.token) viewModel.store.setToken(token)
    }
    LaunchedEffect(zenKey, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(500)
        if (zenKey != settings.zenApiKey) viewModel.store.setZenApiKey(zenKey)
    }
    LaunchedEffect(baseUrl, loaded) {
        if (!loaded || baseUrl == settings.baseUrl || baseUrl.isBlank()) return@LaunchedEffect
        delay(600)
        viewModel.store.setBaseUrl(baseUrl, manual = true)
    }
    LaunchedEffect(maxTokensText, thresholdPct, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(400)
        maxTokensText.toLongOrNull()?.let(viewModel::setMaxContextTokens)
        viewModel.setCompactThreshold(thresholdPct)
    }

    val signedIn = settings.token.isNotBlank()
    val agentMode = AgentMode.byId(settings.agentMode)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ---- Account card ----
            ClaudeGroup {
                ClaudeRow(
                    title = if (signedIn) "Signed in to Kimi" else "Not signed in",
                    value = if (signedIn) "K3 and the K2 family unlocked"
                    else "Free OpenCode Zen models only",
                    trailing = {
                        if (signedIn) {
                            ClaudePill(
                                "Connected",
                                background = MaterialTheme.colorScheme.onSurface,
                                foreground = MaterialTheme.colorScheme.background,
                            )
                        }
                    },
                )
                ClaudeDivider()
                if (signedIn) {
                    ClaudeRow(
                        title = "Sign out",
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
                    ClaudeRow(
                        title = "Sign in with browser",
                        icon = Icons.AutoMirrored.Filled.Login,
                        accent = true,
                        onClick = onOpenLogin,
                    )
                }
            }

            // ---- Connection ----
            GroupLabel("Connection")
            ClaudeGroup {
                ClaudeRow(
                    title = "Kimi proxy",
                    value = when {
                        discoveringProxy -> "Searching…"
                        settings.baseUrl.isBlank() -> "Not found — tap to search"
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
                ClaudeDivider()
                ClaudeRow(
                    title = "API keys",
                    value = if (settings.zenApiKey.isBlank()) "Free models only"
                    else "Zen key set",
                    icon = Icons.Default.Key,
                    onClick = { showKeys = !showKeys },
                    trailing = { Chevron() },
                )
                if (showKeys) {
                    ClaudeDivider()
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Kimi refresh token") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (showToken) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        if (showToken) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = zenKey,
                            onValueChange = { zenKey = it },
                            label = { Text("OpenCode Zen key (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (showZenKey) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showZenKey = !showZenKey }) {
                                    Icon(
                                        if (showZenKey) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- Capabilities ----
            GroupLabel("Capabilities")
            ClaudeGroup {
                ClaudeRow(
                    title = "Tool access",
                    value = agentMode.label,
                    icon = Icons.Default.Science,
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Connectors",
                    value = if (settings.customMcpServers.isEmpty()) "None"
                    else "${settings.customMcpServers.size} connected",
                    icon = Icons.Default.Cable,
                    onClick = onOpenMarketplace,
                    trailing = { Chevron() },
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Marketplace",
                    value = "${settings.installedSkills.size} tools enabled",
                    icon = Icons.Default.Storefront,
                    onClick = onOpenMarketplace,
                    trailing = { Chevron() },
                )
            }

            // ---- Context ----
            GroupLabel("Context")
            ClaudeGroup {
                ClaudeRow(
                    title = "Usage",
                    value = "${(contextState.pct * 100).toInt()}% · " +
                        "${Models.compactTokens(contextState.tokens)} of " +
                        Models.compactTokens(contextState.maxTokens),
                    icon = Icons.Default.Memory,
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Auto-compact",
                    value = "Summarize old turns at $thresholdPct%",
                    icon = Icons.Default.Tune,
                    trailing = {
                        ClaudeToggle(
                            checked = settings.autoCompact,
                            onCheckedChange = viewModel::setAutoCompact,
                        )
                    },
                )
                if (settings.autoCompact) {
                    ClaudeDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Slider(
                            value = thresholdPct.toFloat(),
                            onValueChange = { thresholdPct = it.toInt() },
                            valueRange = 40f..95f,
                        )
                    }
                }
                ClaudeDivider()
                ClaudeRow(
                    title = if (isCompacting) "Compacting…" else "Compact now",
                    onClick = { if (!isCompacting) viewModel.compactNow() },
                    trailing = {
                        if (isCompacting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    },
                )
            }

            // ---- App ----
            GroupLabel("App")
            ClaudeGroup {
                ClaudeRow(
                    title = "Keep runs awake",
                    value = "Hold a wakelock during agent runs",
                    icon = Icons.Default.Bolt,
                    trailing = {
                        ClaudeToggle(
                            checked = settings.keepAwake,
                            onCheckedChange = viewModel::setKeepAwake,
                        )
                    },
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Updates",
                    value = "${settings.updateChannel.lowercase()
                        .replaceFirstChar { it.uppercase() }} · v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onOpenUpdates,
                    trailing = { Chevron() },
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Advanced",
                    value = if (showAdvanced) "Hide" else "Proxy URL override",
                    icon = Icons.Default.Tune,
                    onClick = { showAdvanced = !showAdvanced },
                    trailing = { Chevron() },
                )
                if (showAdvanced) {
                    ClaudeDivider()
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Proxy URL") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = maxTokensText,
                            onValueChange = { new -> maxTokensText = new.filter { it.isDigit() }.take(9) },
                            label = { Text("Max context tokens") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- Conversation ----
            GroupLabel("Conversation")
            ClaudeGroup {
                ClaudeRow(
                    title = "Clear this chat",
                    icon = Icons.Default.DeleteOutline,
                    onClick = { viewModel.clear() },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
