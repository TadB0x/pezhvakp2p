package com.pezhvak.p2p.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pezhvak.p2p.core.db.entities.ConversationEntity
import com.pezhvak.p2p.core.db.entities.ConversationType

@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit,
    onNewChat: () -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Messages", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { /* search */ }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                )
                SearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Default.Edit, "New chat")
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyConversations(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(
                    items = conversations.filter {
                        searchQuery.isEmpty() ||
                        it.name.contains(searchQuery, ignoreCase = true)
                    },
                    key = { it.id }
                ) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
fun ConversationItem(conversation: ConversationEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Surface(
            modifier = Modifier.size(52.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val icon = when (conversation.type) {
                    ConversationType.GROUP -> Icons.Default.Group
                    ConversationType.CHANNEL -> Icons.Default.Campaign
                    ConversationType.FORUM -> Icons.Default.Forum
                    ConversationType.DM -> Icons.Default.Person
                }
                Icon(icon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.isPinned) {
                    Icon(Icons.Default.PushPin, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = formatConversationTime(conversation.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (conversation.unreadCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.lastMessagePreview ?: "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.isMuted) {
                    Icon(Icons.Default.VolumeOff, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline)
                }
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(conversation.unreadCount.toString()) }
                }
            }
        }
    }
}

@Composable
fun SearchBar(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Search conversations") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Default.Clear, null) } }
        } else null,
        modifier = modifier,
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
fun EmptyConversations(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.ChatBubbleOutline, null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
        Text("Start a chat with someone nearby", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatConversationTime(ms: Long): String {
    if (ms == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(ms))
        }
    }
}
