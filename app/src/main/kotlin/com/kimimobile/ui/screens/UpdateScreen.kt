package com.kimimobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kimimobile.BuildConfig
import com.kimimobile.data.AppUpdateInstaller
import com.kimimobile.data.ReleaseInfo
import com.kimimobile.data.UpdateChannel
import com.kimimobile.data.Updater
import com.kimimobile.ui.ChatViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsState()

    val channel = runCatching { UpdateChannel.valueOf(settings.updateChannel) }
        .getOrDefault(UpdateChannel.STABLE)

    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checkedOnce by rememberSaveable { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<AppUpdateInstaller.Progress?>(null) }
    var pendingNightlySwitch by remember { mutableStateOf(false) }

    suspend fun check(force: Boolean) {
        checking = true
        Updater.fetchLatest(channel)
            .onSuccess {
                release = it
                checkedOnce = true
                if (force && it == null) snackbarHostState.showSnackbar("No builds published yet")
            }
            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Update check failed") }
        checking = false
    }

    // Check once on entry, and again whenever the channel changes.
    LaunchedEffect(channel) { check(force = false) }

    val current = BuildConfig.VERSION_NAME
    val latest = release
    val updateAvailable = latest != null && Updater.isNewer(latest.tag, current)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Updates", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { check(force = true) } },
                        enabled = !checking && !downloading,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check now")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- Channel picker ----
            Text(
                "Release channel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                UpdateChannel.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = channel == entry,
                        onClick = {
                            if (entry == UpdateChannel.NIGHTLY && channel != entry) {
                                pendingNightlySwitch = true
                            } else {
                                viewModel.setUpdateChannel(entry.name)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = UpdateChannel.entries.size,
                        ),
                    ) {
                        Text(entry.label)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                channel.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            // ---- Status card ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (updateAvailable)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            checking -> CircularProgressIndicator(
                                Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            updateAvailable -> Icon(
                                Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            else -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    checking -> "Checking for updates…"
                                    updateAvailable -> "Update available"
                                    checkedOnce -> "You're up to date"
                                    else -> "Tap refresh to check"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (updateAvailable)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (updateAvailable && latest != null)
                                    "${latest.name} · installed $current"
                                else "Installed $current",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (updateAvailable)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (updateAvailable && latest != null) {
                        if (latest.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    latest.notes.take(600),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = downloading,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Column(Modifier.padding(top = 12.dp)) {
                                val fraction = progress?.fraction
                                if (fraction != null) {
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = progress?.let {
                                        String.format(
                                            Locale.US,
                                            "%.1f MB of %.1f MB",
                                            it.downloaded / 1_048_576f,
                                            it.total / 1_048_576f,
                                        )
                                    } ?: "Starting download…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (latest.downloadUrl != null) {
                                Button(
                                    onClick = {
                                        downloading = true
                                        scope.launch {
                                            AppUpdateInstaller.downloadAndInstall(
                                                context = context,
                                                url = latest.downloadUrl,
                                                onProgress = { progress = it },
                                            ).onFailure {
                                                snackbarHostState.showSnackbar(
                                                    it.message ?: "Download failed"
                                                )
                                            }
                                            downloading = false
                                            progress = null
                                        }
                                    },
                                    enabled = !downloading,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (downloading) "Downloading…" else "Install")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl))
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Details")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Updates install through Android's package installer — you'll be asked to " +
                    "confirm, and may need to allow installs from this app once.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pendingNightlySwitch) {
        AlertDialog(
            onDismissRequest = { pendingNightlySwitch = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Switch to nightly builds?") },
            text = {
                Text(
                    "Nightly builds come straight off the dev branch. They get new features " +
                        "first, but they can crash, lose data, or fail to start. " +
                        "You can switch back to stable at any time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setUpdateChannel(UpdateChannel.NIGHTLY.name)
                    pendingNightlySwitch = false
                }) {
                    Text("Use nightly")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNightlySwitch = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
