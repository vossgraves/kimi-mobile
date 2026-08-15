package com.kimimobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kimimobile.ui.theme.Claude

/**
 * Claude's composer: one rounded card holding the input, a "+" button, the
 * model pill and send — rather than a text field with controls bolted around
 * it. The context ring floats above the card, anchored bottom-left, so it's
 * always visible without being part of the input.
 */
@Composable
fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    modelLabel: String,
    modelSuffix: String?,
    pendingImages: List<String>,
    onRemoveImage: (String) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (pendingImages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
            ) {
                items(pendingImages) { data ->
                    Box {
                        AsyncImage(
                            model = data,
                            contentDescription = "Attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim)
                                .clickable { onRemoveImage(data) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            "Chat with Kimi…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // "+" — attachments and capabilities
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onOpenAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add to chat",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Model pill: name in white, mode in grey, exactly as Claude.
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onOpenModel)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modelLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!modelSuffix.isNullOrBlank()) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            modelSuffix,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Always present: it's Stop while a reply streams, Send
                // otherwise. Hiding it mid-stream would strand the run with no
                // way to cancel.
                val canSend = input.isNotBlank() || pendingImages.isNotEmpty()
                val active = isStreaming || canSend
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable(enabled = active) {
                            if (isStreaming) onStop() else onSend()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isStreaming) Icons.Default.Stop
                        else Icons.Default.ArrowUpward,
                        contentDescription = if (isStreaming) "Stop" else "Send",
                        tint = if (active) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

/**
 * Context usage, floating over the conversation at the bottom-left — not
 * attached to the composer. Small enough to ignore, precise enough to act on.
 */
@Composable
fun FloatingContextRing(
    pct: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = when {
        pct >= 0.85 -> MaterialTheme.colorScheme.error
        pct >= 0.6 -> Claude.Terracotta
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
            CircularProgressIndicator(
                progress = { pct.toFloat().coerceIn(0f, 1f) },
                color = ringColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            "${(pct * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = ringColor,
        )
    }
}
