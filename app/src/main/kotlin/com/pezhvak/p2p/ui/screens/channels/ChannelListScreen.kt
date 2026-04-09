package com.pezhvak.p2p.ui.screens.channels

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
import com.pezhvak.p2p.core.db.entities.ChannelEntity

@Composable
fun ChannelListScreen(
    onChannelClick: (String) -> Unit,
    onCreateChannel: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val channels by viewModel.subscribedChannels.collectAsState(initial = emptyList())
    var showDiscover by remember { mutableStateOf(false) }
    val allChannels by viewModel.allChannels.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channels", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showDiscover = !showDiscover }) {
                        Icon(Icons.Default.Explore, "Discover")
                    }
                    IconButton(onClick = onCreateChannel) {
                        Icon(Icons.Default.Add, "Create channel")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (showDiscover) {
                item {
                    Text("Discover", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp))
                }
                items(allChannels.filter { !it.isSubscribed }) { channel ->
                    ChannelItem(channel, onClick = { onChannelClick(channel.id) },
                        action = {
                            TextButton(onClick = { viewModel.subscribe(channel.id) }) {
                                Text("Join")
                            }
                        }
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item {
                    Text("Subscribed", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            items(channels, key = { it.id }) { channel ->
                ChannelItem(channel, onClick = { onChannelClick(channel.id) })
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
            if (channels.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(64.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Campaign, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No channels subscribed")
                            TextButton(onClick = { showDiscover = true }) {
                                Text("Discover channels")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelItem(
    channel: ChannelEntity,
    onClick: () -> Unit,
    action: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, fontWeight = FontWeight.Medium)
                if (!channel.isPublic) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Lock, "Private",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline)
                }
            }
        },
        supportingContent = {
            Text(channel.about ?: "No description",
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Campaign, null,
                        tint = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        trailingContent = action,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun ChannelScreen(
    channelId: String,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val messages by viewModel.channelMessages(channelId).collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("Channel") }
            )
        },
        bottomBar = {
            // Channel post input (only for channel owners/admins)
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                        .navigationBarsPadding().imePadding(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Broadcast message…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.postToChannel(channelId, input)
                                input = ""
                            }
                        }
                    ) { Icon(Icons.Default.Send, "Post") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.eventId }) { msg ->
                ChannelMessageItem(msg)
            }
        }
    }
}

@Composable
fun ChannelMessageItem(msg: com.pezhvak.p2p.core.db.entities.MessageEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(msg.contentPlain ?: "…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(msg.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
