package com.kimimobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

/**
 * First-run screen. Rather than dropping you into a chat that can't send
 * anything, ask how you want to connect: Kimi account, an OpenCode Zen key,
 * or nothing at all (Zen's free models).
 */
@Composable
fun WelcomeScreen(
    onSignInKimi: () -> Unit,
    onUseZenKey: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "K",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Kimi Mobile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Search, vision, reasoning and agents — pick how you want to connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        OnboardingOption(
            icon = Icons.Default.AutoAwesome,
            title = "Sign in with Kimi",
            subtitle = "Your Kimi account — K2 models, web search, deep research and vision",
            highlighted = true,
            onClick = onSignInKimi,
        )
        Spacer(Modifier.height(12.dp))
        OnboardingOption(
            icon = Icons.Default.Key,
            title = "Use an OpenCode Zen key",
            subtitle = "Unlocks Kimi K3, Claude, GPT, Gemini and more through Zen",
            onClick = onUseZenKey,
        )
        Spacer(Modifier.height(12.dp))
        OnboardingOption(
            icon = Icons.Default.Bolt,
            title = "Start without an account",
            subtitle = "Zen's free models work with no sign-in and no key",
            onClick = onSkip,
        )

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSkip) {
            Text("Decide later")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun OnboardingOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
