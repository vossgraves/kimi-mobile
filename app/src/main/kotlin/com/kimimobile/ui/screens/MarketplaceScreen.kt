package com.kimimobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kimimobile.data.CatalogItem
import com.kimimobile.data.CatalogType
import com.kimimobile.data.Marketplace
import com.kimimobile.ui.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = remember(query, category) {
        Marketplace.catalog.filter { item ->
            (category == null || item.category == category) &&
                (query.isBlank() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.description.contains(query, ignoreCase = true))
        }
    }

    MarketplaceScreenContent(
        items = filtered,
        installed = settings.installedSkills,
        query = query,
        category = category,
        categories = Marketplace.categories(),
        onQueryChange = { query = it },
        onCategoryChange = { category = it },
        onToggle = viewModel::toggleSkill,
        onBack = onBack,
    )
}

/** Stateless marketplace UI — screenshot-testable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreenContent(
    items: List<CatalogItem>,
    installed: Set<String>,
    query: String,
    category: String?,
    categories: List<String>,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Marketplace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Skills, connectors & MCP servers",
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
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search skills and connectors…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { onCategoryChange(null) },
                    label = { Text("All") },
                )
                categories.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { onCategoryChange(c) },
                        label = { Text(c) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    MarketplaceCard(
                        item = item,
                        installed = item.id in installed,
                        onToggle = { onToggle(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketplaceCard(
    item: CatalogItem,
    installed: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (item.type == CatalogType.SKILL) Icons.Default.Extension else Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.type == CatalogType.SKILL)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            if (item.type == CatalogType.SKILL) "skill" else "connector",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.type == CatalogType.SKILL)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(16.dp),
                color = if (installed) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (installed) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (installed) "Installed" else "Install",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (installed) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
