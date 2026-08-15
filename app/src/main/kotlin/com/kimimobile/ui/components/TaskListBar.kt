package com.kimimobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class TaskStatus { PENDING, ACTIVE, DONE }

data class AgentTask(
    val text: String,
    val status: TaskStatus = TaskStatus.PENDING,
)

/**
 * Task list in the opencode CLI's idiom: a monospace checklist with
 * [ ] / [~] / [x] markers, docked above the composer. Collapsed it shows the
 * active line and a count; expanded it's the whole list.
 */
@Composable
fun TaskListBar(
    tasks: List<AgentTask>,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    var expanded by remember { mutableStateOf(true) }

    val done = tasks.count { it.status == TaskStatus.DONE }
    val active = tasks.firstOrNull { it.status == TaskStatus.ACTIVE }
    val allDone = done == tasks.size
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "task-arrow")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allDone) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (allDone) "All steps complete" else active?.text ?: "Working…",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$done/${tasks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp)
                        .rotate(arrow),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    tasks.forEach { task ->
                        Row(verticalAlignment = Alignment.Top) {
                            // CLI-style status marker.
                            Text(
                                text = when (task.status) {
                                    TaskStatus.DONE -> "[x]"
                                    TaskStatus.ACTIVE -> "[~]"
                                    TaskStatus.PENDING -> "[ ]"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = when (task.status) {
                                    TaskStatus.DONE -> MaterialTheme.colorScheme.primary
                                    TaskStatus.ACTIVE -> MaterialTheme.colorScheme.tertiary
                                    TaskStatus.PENDING -> MaterialTheme.colorScheme.outline
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = task.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = when (task.status) {
                                    TaskStatus.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                                    TaskStatus.ACTIVE -> MaterialTheme.colorScheme.onSurface
                                    TaskStatus.PENDING -> MaterialTheme.colorScheme.outline
                                },
                                textDecoration = if (task.status == TaskStatus.DONE)
                                    TextDecoration.LineThrough else null,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
