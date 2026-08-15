package com.kimimobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimimobile.data.McpRegistry
import com.kimimobile.data.McpServer
import com.kimimobile.data.SkillEngine
import com.kimimobile.ui.ChatViewModel
import kotlinx.coroutines.delay

private enum class MarketTab(val label: String) {
    TOOLS("Built-in"),
    SERVERS("MCP servers"),
    CUSTOM("Custom"),
}

/**
 * Marketplace backed by the official MCP registry rather than a hardcoded
 * list, plus the built-in tools and a tab for adding your own sources.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    var tab by rememberSaveable { mutableStateOf(MarketTab.TOOLS) }
    var query by rememberSaveable { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<McpServer>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Debounced registry search.
    LaunchedEffect(query, tab) {
        if (tab != MarketTab.SERVERS) return@LaunchedEffect
        loading = true
        loadError = null
        delay(350)
        McpRegistry.search(query)
            .onSuccess { servers = it }
            .onFailure { loadError = it.message ?: "Couldn't reach the registry" }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Marketplace", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tools, MCP servers and your own sources",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add custom source")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketTab.entries.forEach { entry ->
                    FilterChip(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        label = { Text(entry.label) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            when (tab) {
                MarketTab.TOOLS -> BuiltInTools(
                    installed = settings.installedSkills,
                    onToggle = viewModel::toggleSkill,
                )

                MarketTab.SERVERS -> Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search the MCP registry") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "registry.modelcontextprotocol.io · servers with an HTTPS endpoint " +
                            "run from this app; package-only ones need a desktop host",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        loading -> Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        loadError != null -> Text(
                            loadError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )

                        else -> LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.navigationBarsPadding(),
                        ) {
                            items(servers, key = { it.name }) { server ->
                                ServerCard(
                                    server = server,
                                    installed = server.remoteUrl != null &&
                                        settings.customMcpServers.any { it.endsWith("|${server.remoteUrl}") },
                                    onInstall = {
                                        val entry = "${server.shortName}|${server.remoteUrl}"
                                        val next = settings.customMcpServers.toMutableSet()
                                        if (!next.add(entry)) next.remove(entry)
                                        viewModel.setCustomMcpServers(next)
                                    },
                                )
                            }
                        }
                    }
                }

                MarketTab.CUSTOM -> CustomSources(
                    servers = settings.customMcpServers,
                    registries = settings.customRegistries,
                    onRemoveServer = { entry ->
                        viewModel.setCustomMcpServers(settings.customMcpServers - entry)
                    },
                    onRemoveRegistry = { entry ->
                        viewModel.setCustomRegistries(settings.customRegistries - entry)
                    },
                    onAdd = { showAddDialog = true },
                )
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAddServer = { label, url ->
                viewModel.setCustomMcpServers(settings.customMcpServers + "$label|$url")
                showAddDialog = false
            },
            onAddRegistry = { url ->
                viewModel.setCustomRegistries(settings.customRegistries + url)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun BuiltInTools(installed: Set<String>, onToggle: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.navigationBarsPadding(),
    ) {
        items(SkillEngine.all, key = { it.id }) { skill ->
            val on = skill.id in installed
            Surface(
                onClick = { onToggle(skill.id) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            skill.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (on) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Enabled",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(server: McpServer, installed: Boolean, onInstall: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (server.installable) Icons.Default.Cloud else Icons.Default.Computer,
                contentDescription = null,
                tint = if (server.installable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    server.shortName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    server.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (server.installable) "remote · v${server.version}"
                    else "desktop only · v${server.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (server.installable) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onInstall) {
                    Text(if (installed) "Remove" else "Add")
                }
            }
        }
    }
}

@Composable
private fun CustomSources(
    servers: Set<String>,
    registries: Set<String>,
    onRemoveServer: (String) -> Unit,
    onRemoveRegistry: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.navigationBarsPadding(),
    ) {
        item {
            Surface(
                onClick = onAdd,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Add MCP server or registry",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        if (servers.isNotEmpty()) {
            item {
                Text(
                    "Your MCP servers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(servers.toList()) { entry ->
                CustomRow(
                    title = entry.substringBefore('|'),
                    subtitle = entry.substringAfter('|'),
                    onRemove = { onRemoveServer(entry) },
                )
            }
        }
        if (registries.isNotEmpty()) {
            item {
                Text(
                    "Extra registries",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(registries.toList()) { entry ->
                CustomRow(title = entry, subtitle = "registry", onRemove = { onRemoveRegistry(entry) })
            }
        }
    }
}

@Composable
private fun CustomRow(title: String, subtitle: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddServer: (String, String) -> Unit,
    onAddRegistry: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isRegistry by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRegistry) "Add registry" else "Add MCP server") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isRegistry,
                        onClick = { isRegistry = false },
                        label = { Text("MCP server") },
                    )
                    FilterChip(
                        selected = isRegistry,
                        onClick = { isRegistry = true },
                        label = { Text("Registry") },
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (!isRegistry) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(if (isRegistry) "Registry URL" else "Server URL (https)") },
                    placeholder = {
                        Text(
                            if (isRegistry) "https://registry.example.com"
                            else "https://server.example.com/mcp"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && (isRegistry || label.isNotBlank()),
                onClick = {
                    if (isRegistry) onAddRegistry(url.trim())
                    else onAddServer(label.trim(), url.trim())
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
