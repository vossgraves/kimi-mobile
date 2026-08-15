package com.kimimobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kimimobile.data.ImageAttachments
import com.kimimobile.data.Models
import com.kimimobile.data.SkillEngine
import com.kimimobile.ui.ChatMessage
import com.kimimobile.ui.ChatViewModel
import com.kimimobile.ui.ContextState
import com.kimimobile.ui.MessageRole
import com.kimimobile.ui.components.MarkdownText
import com.kimimobile.ui.components.ThinkingBlock
import com.kimimobile.ui.components.TypingIndicator
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenMarketplace: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isAgentTurn by viewModel.isAgentTurn.collectAsState()
    val isCompacting by viewModel.isCompacting.collectAsState()
    val error by viewModel.error.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val contextState by viewModel.contextState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pendingImages by viewModel.pendingImages.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var showContextSheet by rememberSaveable { mutableStateOf(false) }
    var showToolsSheet by rememberSaveable { mutableStateOf(false) }
    var showModelSheet by rememberSaveable { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                ImageAttachments.toDataUrl(context, uri)
                    .onSuccess(viewModel::attachImage)
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't attach image") }
            }
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) { viewModel.ensureConnected() }

    val currentModel = Models.byId(settings.model) ?: Models.default

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
        agentEnabled = settings.agentEnabled,
        modelName = currentModel.name,
        searchEnabled = settings.searchEnabled,
        researchEnabled = settings.researchEnabled,
        mathEnabled = settings.mathEnabled,
        pendingImages = pendingImages,
        supportsVision = currentModel.vision,
        onAttachImage = {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onRemoveImage = viewModel::removeImage,
        onRetry = viewModel::retryLast,
        onOpenContextSheet = { showContextSheet = true },
        onOpenToolsSheet = { showToolsSheet = true },
        onOpenModelSheet = { showModelSheet = true },
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

    if (showModelSheet) {
        ModelSheet(
            selectedId = settings.model,
            onSelect = {
                viewModel.setModel(it)
                showModelSheet = false
            },
            onDismiss = { showModelSheet = false },
        )
    }

    if (showToolsSheet) {
        ToolsSheet(
            agentEnabled = settings.agentEnabled,
            searchEnabled = settings.searchEnabled,
            researchEnabled = settings.researchEnabled,
            mathEnabled = settings.mathEnabled,
            installedSkills = settings.installedSkills,
            onAgentToggle = viewModel::setAgentEnabled,
            onSearchToggle = viewModel::setSearchEnabled,
            onResearchToggle = viewModel::setResearchEnabled,
            onMathToggle = viewModel::setMathEnabled,
            onToggleSkill = viewModel::toggleSkill,
            onOpenMarketplace = {
                showToolsSheet = false
                onOpenMarketplace()
            },
            onDismiss = { showToolsSheet = false },
        )
    }
}

/** Stateless chat UI — safe to preview/screenshot with fake data. */
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
    agentEnabled: Boolean = false,
    modelName: String = "K2 · 0905",
    searchEnabled: Boolean = false,
    researchEnabled: Boolean = false,
    mathEnabled: Boolean = false,
    pendingImages: List<String> = emptyList(),
    supportsVision: Boolean = false,
    onAttachImage: () -> Unit = {},
    onRemoveImage: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenContextSheet: () -> Unit = {},
    onOpenToolsSheet: () -> Unit = {},
    onOpenModelSheet: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // Model pill doubles as the status line — tap to switch models.
                    Surface(
                        onClick = onOpenModelSheet,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val dotColor = when {
                                isStreaming -> MaterialTheme.colorScheme.tertiary
                                isConnected == true -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    modelName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = when {
                                        isStreaming && isAgentTurn -> "agent working…"
                                        isStreaming -> "responding…"
                                        isConnected == true -> "connected"
                                        else -> "tap to choose model"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "Switch model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
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
                // Active capability chips — always visible so nothing is silently on.
                ActiveCapabilityRow(
                    agentEnabled = agentEnabled,
                    searchEnabled = searchEnabled,
                    researchEnabled = researchEnabled,
                    mathEnabled = mathEnabled,
                )
                ChatMessageList(
                    messages = messages,
                    isStreaming = isStreaming,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
                Composer(
                    input = input,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    enabled = !isStreaming,
                    agentEnabled = agentEnabled,
                    pendingImages = pendingImages,
                    supportsVision = supportsVision,
                    onAttachImage = onAttachImage,
                    onRemoveImage = onRemoveImage,
                    onOpenToolsSheet = onOpenToolsSheet,
                    contextState = contextState,
                    onOpenContextSheet = onOpenContextSheet,
                )
            }
        }
    }
}

@Composable
private fun ActiveCapabilityRow(
    agentEnabled: Boolean,
    searchEnabled: Boolean,
    researchEnabled: Boolean,
    mathEnabled: Boolean,
) {
    val active = buildList {
        if (agentEnabled) add("Agent" to Icons.Default.Science)
        if (researchEnabled) add("Deep research" to Icons.Default.TravelExplore)
        else if (searchEnabled) add("Web search" to Icons.Default.TravelExplore)
        if (mathEnabled) add("Math" to Icons.Default.Calculate)
    }
    AnimatedVisibility(
        visible = active.isNotEmpty(),
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            active.forEach { (label, icon) ->
                AssistChip(
                    onClick = {},
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, Modifier.size(14.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                )
            }
        }
    }
}

// ---- Context ring & sheet ----------------------------------------------------

@Composable
private fun ContextRing(
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { context.pct.toFloat().coerceIn(0f, 1f) },
                color = ringColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
            )
            Text(
                text = "${(context.pct * 100).toInt().coerceAtMost(99)}",
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
                "Context window",
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
                "Estimated locally — the web API reports no usage.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Text("Threshold", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCompactNow, enabled = !isCompacting) {
                Text(if (isCompacting) "Compacting…" else "Compact now")
            }
        }
    }
}

// ---- Model sheet -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Choose a model",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(420.dp),
            ) {
                items(Models.all, key = { it.id }) { model ->
                    val selected = model.id == selectedId
                    Surface(
                        onClick = { onSelect(model.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    model.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (model.vision) CapabilityTag("vision")
                                if (model.reasoning) {
                                    Spacer(Modifier.width(4.dp))
                                    CapabilityTag("thinking")
                                }
                            }
                            Text(
                                model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                String.format(Locale.US, "%,d tokens", model.contextTokens),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityTag(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ---- Tools sheet -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolsSheet(
    agentEnabled: Boolean,
    searchEnabled: Boolean,
    researchEnabled: Boolean,
    mathEnabled: Boolean,
    installedSkills: Set<String>,
    onAgentToggle: (Boolean) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onResearchToggle: (Boolean) -> Unit,
    onMathToggle: (Boolean) -> Unit,
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
            Text(
                "Kimi capabilities",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Run server-side by Kimi itself",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            CapabilityToggle(
                title = "Web search",
                subtitle = "Answer from live results with citations",
                checked = searchEnabled,
                enabled = !researchEnabled,
                onCheckedChange = onSearchToggle,
            )
            CapabilityToggle(
                title = "Deep research",
                subtitle = "Multi-step research — slower, uses your daily research quota",
                checked = researchEnabled,
                onCheckedChange = onResearchToggle,
            )
            CapabilityToggle(
                title = "Math mode",
                subtitle = "Step-by-step problem solving",
                checked = mathEnabled,
                onCheckedChange = onMathToggle,
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Text(
                "On-device agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            CapabilityToggle(
                title = "Agent mode",
                subtitle = "The model calls local tools in a loop to finish tasks",
                checked = agentEnabled,
                onCheckedChange = onAgentToggle,
            )
            if (agentEnabled) {
                SkillEngine.all.forEach { skill ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(skill.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                skill.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = skill.id in installedSkills,
                            onCheckedChange = { onToggleSkill(skill.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onOpenMarketplace,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                }
            }
        }
    }
}

@Composable
private fun CapabilityToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ---- Message list ------------------------------------------------------------

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, isStreaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showTyping: Boolean,
    onRetry: () -> Unit,
) {
    if (message.notice) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            if (message.images.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    items(message.images) { data ->
                        AsyncImage(
                            model = data,
                            contentDescription = "Attached image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    }
                }
            }
            if (message.content.isNotBlank()) {
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
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
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
            Column(Modifier.weight(1f)) {
                if (message.reasoning.isNotBlank()) {
                    ThinkingBlock(
                        reasoning = message.reasoning,
                        streaming = showTyping,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                    modifier = Modifier.widthIn(max = 560.dp),
                ) {
                    when {
                        showTyping && message.content.isEmpty() ->
                            TypingIndicator(modifier = Modifier.padding(16.dp))

                        message.failed -> Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "That request didn't go through.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }

                        else -> MarkdownText(
                            markdown = message.content,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
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
                text = "K",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Kimi Mobile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Search, vision, reasoning and agents — free via the web API",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Summarize today's AI news",
                "Explain this screenshot",
                "Write a Kotlin coroutine retry helper",
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
    pendingImages: List<String>,
    supportsVision: Boolean,
    onAttachImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onOpenToolsSheet: () -> Unit,
    contextState: ContextState,
    onOpenContextSheet: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Staged attachments
            if (pendingImages.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(pendingImages) { data ->
                        Box {
                            AsyncImage(
                                model = data,
                                contentDescription = "Attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Surface(
                                onClick = { onRemoveImage(data) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.padding(3.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                // Context ring, bottom-left as requested.
                ContextRing(
                    context = contextState,
                    onClick = onOpenContextSheet,
                    modifier = Modifier.padding(end = 6.dp),
                )
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
                    leadingIcon = {
                        IconButton(onClick = onOpenToolsSheet) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Capabilities & tools",
                                tint = if (agentEnabled) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = if (supportsVision) {
                        {
                            IconButton(onClick = onAttachImage) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Attach image",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                val canSend = enabled && (input.isNotBlank() || pendingImages.isNotEmpty())
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
}
