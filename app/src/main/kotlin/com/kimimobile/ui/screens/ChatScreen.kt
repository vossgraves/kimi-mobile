package com.kimimobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kimimobile.data.AgentMode
import com.kimimobile.data.ImageAttachments
import com.kimimobile.data.Models
import com.kimimobile.data.Provider
import com.kimimobile.data.ReasoningEffort
import com.kimimobile.ui.ChatMessage
import com.kimimobile.ui.ChatViewModel
import com.kimimobile.ui.ContextState
import com.kimimobile.ui.MessageRole
import com.kimimobile.ui.components.AgentTask
import com.kimimobile.ui.components.ChatDrawer
import com.kimimobile.ui.components.ClaudeDivider
import com.kimimobile.ui.components.ClaudeGroup
import com.kimimobile.ui.components.ClaudeRow
import com.kimimobile.ui.components.ClaudeToggle
import com.kimimobile.ui.components.Composer
import com.kimimobile.ui.components.FloatingContextRing
import com.kimimobile.ui.components.MarkdownText
import com.kimimobile.ui.components.ModelPickerSheet
import com.kimimobile.ui.components.SheetHeader
import com.kimimobile.ui.components.TaskListBar
import com.kimimobile.ui.components.ThinkingBlock
import com.kimimobile.ui.components.TypingIndicator
import com.kimimobile.ui.theme.Claude
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isAgentTurn by viewModel.isAgentTurn.collectAsState()
    val isCompacting by viewModel.isCompacting.collectAsState()
    val error by viewModel.error.collectAsState()
    val contextState by viewModel.contextState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pendingImages by viewModel.pendingImages.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val signInRequired by viewModel.signInRequired.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val conversations by viewModel.conversations.conversations.collectAsState()
    val activeConversationId by viewModel.activeConversationId.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    val addSheetState = rememberModalBottomSheetState()
    val modelSheetState = rememberModalBottomSheetState()
    val contextSheetState = rememberModalBottomSheetState()
    val modeSheetState = rememberModalBottomSheetState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                ImageAttachments.toDataUrl(context, uri)
                    .onSuccess(viewModel::attachImage)
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't attach") }
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

    val currentModel = Models.byId(settings.model)
    val agentMode = AgentMode.byId(settings.agentMode)
    val effort = ReasoningEffort.byId(settings.reasoningEffort)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                conversations = conversations,
                activeId = activeConversationId,
                onNewChat = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onOpenChat = { id ->
                    viewModel.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteChat = viewModel::deleteConversation,
                onOpenMarketplace = {
                    scope.launch { drawerState.close() }
                    onOpenMarketplace()
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        ChatScreenContent(
            messages = messages,
            isStreaming = isStreaming,
            isAgentTurn = isAgentTurn,
            input = input,
            onInputChange = { input = it },
            onSend = {
                viewModel.send(input)
                input = ""
            },
            onStop = viewModel::stopStreaming,
            snackbarHostState = snackbarHostState,
            contextState = contextState,
            tasks = tasks,
            pendingImages = pendingImages,
            modelLabel = currentModel?.name ?: settings.model,
            modelSuffix = when {
                agentMode != AgentMode.CHAT -> agentMode.label
                currentModel?.reasoning == true -> effort.label
                else -> null
            },
            onRemoveImage = viewModel::removeImage,
            onRetry = viewModel::retryLast,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewChat = viewModel::newConversation,
            onOpenAdd = { sheet = Sheet.ADD },
            onOpenModel = { sheet = Sheet.MODEL },
            onOpenContext = { sheet = Sheet.CONTEXT },
        )
    }

    when (sheet) {
        Sheet.ADD -> AddToChatSheet(
            sheetState = addSheetState,
            agentMode = agentMode,
            searchEnabled = settings.searchEnabled,
            researchEnabled = settings.researchEnabled,
            connectorCount = settings.customMcpServers.size,
            onPickPhoto = {
                sheet = null
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                sheet = null
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFile = {
                sheet = null
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSearchToggle = viewModel::setSearchEnabled,
            onResearchToggle = viewModel::setResearchEnabled,
            onOpenModes = { sheet = Sheet.MODE },
            onOpenConnectors = {
                sheet = null
                onOpenMarketplace()
            },
            onDismiss = { sheet = null },
        )

        Sheet.MODE -> ModeSheet(
            sheetState = modeSheetState,
            current = agentMode,
            onSelect = {
                viewModel.setAgentMode(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        Sheet.MODEL -> ModelPickerSheet(
            sheetState = modelSheetState,
            models = availableModels.filterNot { it.hidden }.filter { model ->
                when {
                    model.provider == Provider.KIMI -> settings.token.isNotBlank()
                    model.requiresKey -> settings.zenApiKey.isNotBlank()
                    else -> true
                }
            },
            selectedId = settings.model,
            effort = effort,
            kimiHidden = settings.token.isBlank(),
            onSelect = {
                viewModel.setModel(it)
                sheet = null
            },
            onEffortChange = viewModel::setReasoningEffort,
            onDismiss = { sheet = null },
        )

        Sheet.CONTEXT -> ContextSheet(
            sheetState = contextSheetState,
            context = contextState,
            isCompacting = isCompacting,
            autoCompact = settings.autoCompact,
            thresholdPct = settings.compactThresholdPct,
            onAutoCompactChange = viewModel::setAutoCompact,
            onThresholdChange = viewModel::setCompactThreshold,
            onCompactNow = {
                viewModel.compactNow()
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        null -> Unit
    }

    if (signInRequired) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSignIn,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Sign in to use Kimi") },
            text = {
                Text(
                    "Kimi models need your account. Sign in, or switch to a free " +
                        "OpenCode Zen model that needs no account."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissSignIn()
                    onOpenSettings()
                }) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissSignIn()
                    sheet = Sheet.MODEL
                }) { Text("Use a free model") }
            },
        )
    }
}

private enum class Sheet { ADD, MODE, MODEL, CONTEXT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    isAgentTurn: Boolean = false,
    onStop: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    contextState: ContextState = ContextState(),
    tasks: List<AgentTask> = emptyList(),
    pendingImages: List<String> = emptyList(),
    modelLabel: String = "Kimi K3",
    modelSuffix: String? = null,
    onRemoveImage: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onOpenAdd: () -> Unit = {},
    onOpenModel: () -> Unit = {},
    onOpenContext: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "New chat",
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
                .imePadding(),
        ) {
            Box(Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    EmptyState()
                } else {
                    ChatMessageList(
                        messages = messages,
                        isStreaming = isStreaming,
                        onRetry = onRetry,
                    )
                }

                // Context ring floats over the conversation, bottom-left,
                // independent of the composer.
                if (contextState.tokens > 0) {
                    FloatingContextRing(
                        pct = contextState.pct,
                        onClick = onOpenContext,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 8.dp),
                    )
                }
            }

            TaskListBar(tasks = tasks)

            Composer(
                input = input,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                isStreaming = isStreaming,
                modelLabel = modelLabel,
                modelSuffix = modelSuffix,
                pendingImages = pendingImages,
                onRemoveImage = onRemoveImage,
                onOpenAdd = onOpenAdd,
                onOpenModel = onOpenModel,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

// ---- Context sheet -----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(
    sheetState: androidx.compose.material3.SheetState,
    context: ContextState,
    isCompacting: Boolean,
    autoCompact: Boolean,
    thresholdPct: Int,
    onAutoCompactChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onCompactNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            SheetHeader(title = "Context", onClose = onDismiss)
            Spacer(Modifier.height(8.dp))
            ClaudeGroup {
                ClaudeRow(
                    title = "Used",
                    value = "${(context.pct * 100).toInt()}% · " +
                        "${Models.compactTokens(context.tokens)} of " +
                        Models.compactTokens(context.maxTokens),
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Messages",
                    value = context.messageCount.toString(),
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Auto-compact",
                    value = "Summarize old turns at $thresholdPct%",
                    trailing = {
                        ClaudeToggle(checked = autoCompact, onCheckedChange = onAutoCompactChange)
                    },
                )
                ClaudeDivider()
                ClaudeRow(
                    title = if (isCompacting) "Compacting…" else "Compact now",
                    accent = !isCompacting,
                    onClick = { if (!isCompacting) onCompactNow() },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Estimated locally — the API reports no usage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- Messages ----------------------------------------------------------------

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /**
     * Scrolls so the END of the last message rests at the bottom of the
     * viewport. scrollToItem alone aligns an item's *top* to the viewport top,
     * which for a long reply leaves you stranded mid-message — that's why
     * "jump to latest" appeared to do nothing.
     */
    suspend fun snapToEnd(animate: Boolean) {
        if (messages.isEmpty()) return
        val index = messages.lastIndex
        if (animate) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        // Then push past the remainder of that item, if it's taller than the
        // viewport.
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull { it.index == index } ?: return
        val overshoot = (last.offset + last.size - info.viewportEndOffset).toFloat()
        if (overshoot > 0f) {
            if (animate) listState.animateScrollBy(overshoot) else listState.scrollBy(overshoot)
        }
    }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 80
        }
    }

    // Follow the stream only while parked at the bottom, so scrolling up
    // during a reply actually works.
    var following by remember { mutableStateOf(true) }

    // Resume following only when the user *settles* at the bottom — checking
    // mid-fling made this flip back on immediately after a scroll up.
    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (atBottom && !listState.isScrollInProgress) following = true
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !atBottom) following = false
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, following) {
        if (following) snapToEnd(animate = false)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    showTyping = isStreaming && message == messages.lastOrNull() &&
                        message.role == MessageRole.ASSISTANT,
                    onRetry = onRetry,
                )
            }
        }

        // Keyed on position, not on the follow flag: the button is for getting
        // back to the bottom, so it shows whenever you aren't there.
        AnimatedVisibility(
            visible = !atBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 8.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        following = true
                        scope.launch { snapToEnd(animate = true) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = "Jump to latest",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp),
                )
            }
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
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Context compacted",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Claude.Terracotta,
            )
            Spacer(Modifier.height(6.dp))
            SelectionContainer {
                MarkdownText(
                    markdown = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    if (message.role == MessageRole.USER) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
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
                Box(
                    Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                ) {
                    SelectionContainer {
                        Text(
                            message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        return
    }

    // Assistant: full width, no bubble, no avatar.
    Column(Modifier.fillMaxWidth()) {
        if (message.reasoning.isNotBlank()) {
            ThinkingBlock(
                reasoning = message.reasoning,
                streaming = showTyping,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        when {
            showTyping && message.content.isEmpty() -> TypingIndicator()

            message.failed -> Column {
                Text(
                    "That request didn't go through.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }

            else -> {
                SelectionContainer { MarkdownText(markdown = message.content) }
                if (!showTyping && message.content.isNotBlank()) {
                    MessageActions(message.content)
                }
            }
        }
    }
}

@Composable
private fun MessageActions(content: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(content) { mutableStateOf(false) }

    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(CircleShape)
                .clickable {
                    clipboard.setText(AnnotatedString(content))
                    copied = true
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (copied) "Copied" else "Copy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Terracotta starburst, the app's one moment of colour.
        Text(
            "✳",
            style = MaterialTheme.typography.displaySmall,
            color = Claude.Terracotta,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Back at it",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
