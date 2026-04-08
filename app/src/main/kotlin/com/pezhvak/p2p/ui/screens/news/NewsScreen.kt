package com.pezhvak.p2p.ui.screens.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pezhvak.p2p.core.db.entities.NewsItemEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NewsScreen(viewModel: NewsViewModel = hiltViewModel()) {
    val newsItems by viewModel.newsItems.collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Saved", "Technology", "Politics", "World")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("News", fontWeight = FontWeight.Bold) },
                    actions = {
                        // Mesh distribution indicator
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Hub, "Distributed via mesh",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tab) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (newsItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Newspaper, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("News will arrive via mesh", style = MaterialTheme.typography.titleMedium)
                    Text("Verified & tamper-proof", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(newsItems, key = { it.id }) { item ->
                    NewsCard(
                        item = item,
                        onClick = { viewModel.markRead(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NewsCard(item: NewsItemEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Image
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                // Source + verified badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (item.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, "Verified",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatNewsTime(item.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.summary.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Distribution info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Distributed via ${item.distributedVia.name.lowercase().replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!item.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .padding(0.dp),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

private fun formatNewsTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}
