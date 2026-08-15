package com.kimi3.client.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * Three bouncing dots shown while waiting for the first token.
 * Spring-based bounce per dot with a slight stagger.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // infiniteRepeatable requires a duration-based spec (spring is physics-based, not allowed here)
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "typing-phase",
    )

    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0, 1, 2).forEach { i ->
            val offset = ((phase + i * 0.33f) % 1f)
            val scale = 0.55f + 0.45f * offset
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(primary),
            )
        }
    }
}
