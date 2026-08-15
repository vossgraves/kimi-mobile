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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
 * Live plan tracker docked above the composer, v0-style: collapsed it's a
 * single progress line, expanded it lists each step with its state.
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
        shape = RoundedCornerShape(16.dp),
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allDone) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (allDone) "Done" else active?.text ?: "Working…",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$done of ${tasks.size} steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse plan" else "Expand plan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrow),
                )
            }

            LinearProgressIndicator(
                progress = { done.toFloat() / tasks.size },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tasks.forEach { task ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (task.status) {
                                TaskStatus.DONE -> Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Done",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                TaskStatus.ACTIVE -> CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(13.dp),
                                )
                                TaskStatus.PENDING -> Icon(
                                    Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Pending",
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = task.text,
                                style = MaterialTheme.typography.bodySmall,
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
