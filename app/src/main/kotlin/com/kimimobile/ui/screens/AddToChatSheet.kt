package com.kimimobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kimimobile.data.AgentMode
import com.kimimobile.ui.components.AttachmentTile
import com.kimimobile.ui.components.ClaudeDivider
import com.kimimobile.ui.components.ClaudeGroup
import com.kimimobile.ui.components.ClaudeRow
import com.kimimobile.ui.components.ClaudeToggle
import com.kimimobile.ui.components.SheetHeader

/**
 * The "+" sheet, modelled on Claude's "Add to chat": attachment tiles on top,
 * then grouped rows for capabilities and tools. Partial height — it should
 * never swallow the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToChatSheet(
    sheetState: SheetState,
    agentMode: AgentMode,
    searchEnabled: Boolean,
    researchEnabled: Boolean,
    connectorCount: Int,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onResearchToggle: (Boolean) -> Unit,
    onOpenModes: () -> Unit,
    onOpenConnectors: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            SheetHeader(title = "Add to chat", onClose = onDismiss)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachmentTile(
                    label = "Camera",
                    icon = Icons.Default.CameraAlt,
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f),
                )
                AttachmentTile(
                    label = "Photos",
                    icon = Icons.Default.Image,
                    onClick = onPickPhoto,
                    modifier = Modifier.weight(1f),
                )
                AttachmentTile(
                    label = "Files",
                    icon = Icons.Default.InsertDriveFile,
                    onClick = onPickFile,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Kimi's own server-side capabilities.
            ClaudeGroup {
                ClaudeRow(
                    title = "Research",
                    icon = Icons.Default.TravelExplore,
                    iconInCircle = true,
                    trailing = {
                        ClaudeToggle(checked = researchEnabled, onCheckedChange = onResearchToggle)
                    },
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Web search",
                    icon = Icons.Default.Language,
                    iconInCircle = true,
                    trailing = {
                        ClaudeToggle(
                            checked = searchEnabled,
                            onCheckedChange = onSearchToggle,
                            enabled = !researchEnabled,
                        )
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            ClaudeGroup {
                ClaudeRow(
                    title = "Tool access",
                    value = agentMode.label,
                    icon = Icons.Default.HomeRepairService,
                    iconInCircle = true,
                    onClick = onOpenModes,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                ClaudeDivider()
                ClaudeRow(
                    title = "Connectors",
                    value = if (connectorCount == 0) "None" else "$connectorCount connected",
                    icon = Icons.Default.Cable,
                    iconInCircle = true,
                    onClick = onOpenConnectors,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Tool-access modes, reached from "Add to chat". Same grouped-row language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(
    sheetState: SheetState,
    current: AgentMode,
    onSelect: (AgentMode) -> Unit,
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
            SheetHeader(title = "Tool access", onClose = onDismiss)
            Spacer(Modifier.height(8.dp))
            ClaudeGroup {
                AgentMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) ClaudeDivider()
                    ClaudeRow(
                        title = mode.label,
                        value = mode.tagline,
                        accent = mode == current,
                        onClick = { onSelect(mode) },
                        trailing = {
                            if (mode == current) {
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
