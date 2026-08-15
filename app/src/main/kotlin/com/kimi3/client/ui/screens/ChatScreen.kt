package com.kimi3.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimi3.client.data.SkillEngine
import com.kimi3.client.ui.ChatMessage
import com.kimi3.client.ui.ChatViewModel
import com.kimi3.client.ui.ContextState
import com.kimi3.client.ui.MessageRole
import com.kimi3.client.ui.components.MarkdownText
import com.kimi3.client.ui.components.TypingIndicator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenMarketplace: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isAgentTurn by viewModel.isAgentTurn.collectAsState()
    val isCompacting by viewModel.isCompacting.collectAsState()
    val error by viewModel.error.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val contextState by viewModel.contextState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var showContextSheet by rememberSaveable { mutableStateOf(false) }
    var showAgentSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Warm the connection on first composition.
    LaunchedEffect(Unit) {
        viewModel.ensureConnected()
    }

    ChatScreenContent(
        messages = messages,
        isStreaming = isStreaming,
        isConnected = isConnected,
        input = input,
        onInputChange = { input = it },
        onSend = {
            viewModel.send(input)
            input = ""
        },
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        contextState = contextState,
        isAgentTurn = isAgentTurn,
        isCompacting = isCompacting,
        agentEnabled = settings.agentEnabled,
        installedSkills = settings.installedSkills,
        onAgentToggle = viewModel::setAgentEnabled,
        onToggleSkill = viewModel::toggleSkill,
        onCompactNow = viewModel::compactNow,
        onOpenMarketplace = onOpenMarketplace,
        onOpenContextSheet = { showContextSheet = true },
        onOpenAgentSheet = { showAgentSheet = true },
    )

    if (showContextSheet) {
        ContextSheet(
            context = contextState,
            isCompacting = isCompacting,
            autoCompact = settings.autoCompact,
            thresholdPct = settings.compactThresholdPct,
            onAutoCompactChange = viewModel::setAutoCompact,
            onThresholdChange = viewModel::setCompactThreshold,
            onCompactNow = viewModel::compactNow,
            onDismiss = { showContextSheet = false },
        )
    }

    if (showAgentSheet) {
        AgentSheet(
            agentEnabled = settings.agentEnabled,
            installedSkills = settings.installedSkills,
            onAgentToggle = viewModel::setAgentEnabled,
            onToggleSkill = viewModel::toggleSkill,
            onOpenMarketplace = {
                showAgentSheet = false
                onOpenMarketplace()
            },
            onDismiss = { showAgentSheet = false },
        )
    }
}

/**
 * Stateless chat UI — safe to preview/screenshot with fake data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    isConnected: Boolean?,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
    contextState: ContextState = ContextState(),
    isAgentTurn: Boolean = false,
    isCompacting: Boolean = false,
    agentEnabled: Boolean = false,
    installedSkills: Set<String> = emptySet(),
    onAgentToggle: (Boolean) -> Unit = {},
    onToggleSkill: (String) -> Unit = {},
    onCompactNow: () -> Unit = {},
    onOpenMarketplace: (() -> Unit)? = null,
    onOpenContextSheet: () -> Unit = {},
    onOpenAgentSheet: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Kimi K3",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when {
                                isStreaming -> MaterialTheme.colorScheme.tertiary
                                isConnected == true -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isStreaming && isAgentTurn -> "agent working…"
                                    isStreaming -> "thinking…"
                                    isConnected == true -> "connected"
                                    else -> "offline"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (agentEnabled) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        "agent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                ChatMessageList(
                    messages = messages,
                    isStreaming = isStreaming,
                    modifier = Modifier.weight(1f),
                )
                Composer(
                    input = input,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    enabled = !isStreaming,
                    agentEnabled = agentEnabled,
                    onOpenAgentSheet = onOpenAgentSheet,
                )
            }
            // Bottom-left context ring, floating above the composer (Claude-style).
            ContextRingButton(
                context = contextState,
                onClick = onOpenContextSheet,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 82.dp)
                    .imePadding(),
            )
        }
    }
}

// ---- Context ring & sheet ----------------------------------------------------

@Composable
private fun ContextRingButton(
    context: ContextState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = when {
        context.pct >= 0.8 -> MaterialTheme.colorScheme.error
        context.pct >= 0.6 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
        modifier = modifier.size(46.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { context.pct.toFloat().coerceIn(0f, 1f) },
                color = ringColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
            )
            Text(
                text = "${(context.pct * 100).toInt().coerceAtMost(99)}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = ringColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(
    context: ContextState,
    isCompacting: Boolean,
    autoCompact: Boolean,
    thresholdPct: Int,
    onAutoCompactChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onCompactNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ringColor = when {
                context.pct >= 0.8 -> MaterialTheme.colorScheme.error
                context.pct >= 0.6 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                CircularProgressIndicator(
                    progress = { context.pct.toFloat().coerceIn(0f, 1f) },
                    color = ringColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeWidth = 6.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = "${(context.pct * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ringColor,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Context window",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = String.format(
                    Locale.US,
                    "%,d of %,d tokens · %d messages",
                    context.tokens,
                    context.maxTokens,
                    context.messageCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Estimated locally — the web API reports no usage.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-compact", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Summarize old turns when the window fills up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoCompact, onCheckedChange = onAutoCompactChange)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Threshold",
                    style = MaterialTheme.typography.bodyLarge,
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
                onValueChange = { onThresholdChange(it.toInt()) },
                valueRange = 40f..95f,
                enabled = autoCompact,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onCompactNow,
                enabled = !isCompacting,
            ) {
                Text(if (isCompacting) "Compacting…" else "Compact now")
            }
        }
    }
}

// ---- Agent sheet -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSheet(
    agentEnabled: Boolean,
    installedSkills: Set<String>,
    onAgentToggle: (Boolean) -> Unit,
    onToggleSkill: (String) -> Unit,
    onOpenMarketplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agent mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "The model can call tools — web search, fetching, math — to solve tasks itself",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = agentEnabled, onCheckedChange = onAgentToggle)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tools",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SkillEngine.all.forEach { skill ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(skill.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = skill.id in installedSkills,
                        onCheckedChange = { onToggleSkill(skill.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            if (onOpenMarketplace != null) {
                Surface(
                    onClick = onOpenMarketplace,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Marketplace", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "Skills, connectors & MCP servers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Open",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---- Message list ------------------------------------------------------------

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Follow the latest message while streaming.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, isStreaming) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                showTyping = isStreaming && message == messages.lastOrNull() && message.role == MessageRole.ASSISTANT,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, showTyping: Boolean) {
    if (message.notice) {
        // Compacted-context card: muted, centered, not part of the turn flow.
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Context compacted — earlier turns summarized",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    MarkdownText(
                        markdown = message.content,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        return
    }

    if (message.role == MessageRole.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "K",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                when {
                    showTyping && message.content.isEmpty() -> TypingIndicator(
                        modifier = Modifier.padding(16.dp),
                    )
                    message.failed -> Text(
                        text = "Request failed — check your connection and token in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    else -> MarkdownText(
                        markdown = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "K3",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Chat with Kimi K3",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Free access via the web API, no subscription needed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Explain quantum computing simply",
                "Write a Python function that reverses a string",
                "Draft an email asking for a raise",
            ).forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    agentEnabled: Boolean,
    onOpenAgentSheet: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // "+" — agent mode & tools (Claude-style).
            IconButton(
                onClick = onOpenAgentSheet,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (agentEnabled) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Agent & tools",
                    tint = if (agentEnabled) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(if (agentEnabled) "Ask the agent…" else "Message Kimi…")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            Spacer(Modifier.width(8.dp))
            val canSend = enabled && input.isNotBlank()
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
