package com.kimimobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimimobile.data.KimiModel
import com.kimimobile.data.Models
import com.kimimobile.data.ReasoningEffort

/**
 * Model picker in Claude's shape: a short list of the models you'd actually
 * pick, an Effort row that opens its own step, and everything else behind
 * "More models". Partial height, blue for the current selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    sheetState: SheetState,
    models: List<KimiModel>,
    selectedId: String,
    effort: ReasoningEffort,
    kimiHidden: Boolean,
    onSelect: (String) -> Unit,
    onEffortChange: (ReasoningEffort) -> Unit,
    onDismiss: () -> Unit,
) {
    // Two steps in one sheet: the short list, then the full list or effort.
    var step by remember { mutableStateOf(Step.MODELS) }

    val selected = models.firstOrNull { it.id == selectedId }
    // The headline list stays short — the rest lives behind "More models".
    val featured = remember(models, selectedId) {
        val head = models.take(4)
        if (selected != null && head.none { it.id == selectedId }) head + selected else head
    }

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
            when (step) {
                Step.MODELS -> {
                    SheetHeader(title = "Select model", onClose = onDismiss)
                    Spacer(Modifier.height(8.dp))

                    if (kimiHidden) {
                        ClaudeGroup {
                            ClaudeRow(
                                title = "Sign in to Kimi",
                                value = "Unlocks K3 and the K2 family, free with your account",
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    ClaudeGroup {
                        featured.forEachIndexed { index, model ->
                            if (index > 0) ClaudeDivider()
                            ModelRow(
                                model = model,
                                selected = model.id == selectedId,
                                onClick = { onSelect(model.id) },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Effort only matters for models that actually reason.
                    if (selected?.reasoning == true) {
                        ClaudeGroup {
                            ClaudeRow(
                                title = "Effort",
                                value = effort.label,
                                icon = Icons.Default.Speed,
                                iconInCircle = true,
                                onClick = { step = Step.EFFORT },
                                trailing = { Chevron() },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (models.size > featured.size) {
                        ClaudeGroup {
                            ClaudeRow(
                                title = "More models",
                                icon = Icons.Default.MoreHoriz,
                                iconInCircle = true,
                                onClick = { step = Step.ALL },
                                trailing = { Chevron() },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                Step.ALL -> {
                    SheetHeader(title = "All models", onClose = { step = Step.MODELS })
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        models.groupBy { it.provider }.forEach { (provider, entries) ->
                            GroupLabel(provider.label)
                            ClaudeGroup {
                                entries.forEachIndexed { index, model ->
                                    if (index > 0) ClaudeDivider()
                                    ModelRow(
                                        model = model,
                                        selected = model.id == selectedId,
                                        onClick = { onSelect(model.id) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                Step.EFFORT -> {
                    SheetHeader(title = "Effort", onClose = { step = Step.MODELS })
                    Spacer(Modifier.height(8.dp))
                    ClaudeGroup {
                        ReasoningEffort.entries.forEachIndexed { index, level ->
                            if (index > 0) ClaudeDivider()
                            ClaudeRow(
                                title = level.label,
                                value = level.description,
                                onClick = {
                                    onEffortChange(level)
                                    step = Step.MODELS
                                },
                                trailing = {
                                    if (level == effort) {
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
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private enum class Step { MODELS, ALL, EFFORT }

@Composable
private fun ModelRow(
    model: KimiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface

    ClaudeRow(
        title = "",
        onClick = onClick,
        trailing = {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        content = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = tint,
                    )
                    if (model.requiresKey) {
                        Spacer(Modifier.width(8.dp))
                        ClaudePill("Requires a key")
                    }
                }
                Text(
                    model.description.ifBlank { "${Models.compactTokens(model.contextTokens)} context" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
