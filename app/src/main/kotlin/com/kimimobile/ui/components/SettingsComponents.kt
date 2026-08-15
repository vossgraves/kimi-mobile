package com.kimimobile.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Grouped settings surfaces, ArchiveTune-style: a labelled section header over
 * a single rounded card, so related controls read as one block instead of a
 * flat wall of fields.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(top = 20.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                content()
            }
        }
    }
}

/** A tappable row inside a [SettingsSection]. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier.fillMaxWidth(),
        ) { row() }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier.fillMaxWidth(),
        ) { row() }
    }
}

/** Switch row — the most common settings control. */
@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}
