package com.pezhvak.p2p.ui.screens.forums

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pezhvak.p2p.core.db.entities.ForumThreadEntity

@Composable
fun ForumListScreen(
    onThreadClick: (String) -> Unit,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    val threads by viewModel.threads.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forums", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* new thread */ }) {
                        Icon(Icons.Default.Add, "New thread")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (threads.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(64.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Forum, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No threads yet")
                            Text("Start a discussion!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(threads, key = { it.id }) { thread ->
                    ForumThreadCard(thread, onClick = { onThreadClick(thread.id) })
                }
            }
        }
    }
}

@Composable
fun ForumThreadCard(thread: ForumThreadEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thread.isPinned) {
                    Icon(Icons.Default.PushPin, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                if (thread.isLocked) {
                    Icon(Icons.Default.Lock, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                }
                Text(thread.title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(thread.authorPubKey.take(8) + "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Forum, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("${thread.replyCount} replies",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ForumThreadScreen(
    threadId: String,
    onBack: () -> Unit,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    val messages by viewModel.threadMessages(threadId).collectAsState(initial = emptyList())
    var reply by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("Thread") }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                        .navigationBarsPadding().imePadding(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = reply,
                        onValueChange = { reply = it },
                        placeholder = { Text("Write a reply…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (reply.isNotBlank()) {
                                viewModel.postReply(threadId, reply)
                                reply = ""
                            }
                        }
                    ) { Icon(Icons.Default.Send, "Reply") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.eventId }) { msg ->
                ForumPostCard(msg)
            }
        }
    }
}

@Composable
fun ForumPostCard(msg: com.pezhvak.p2p.core.db.entities.MessageEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(msg.senderPubKey.take(8) + "…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(msg.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(msg.contentPlain ?: "…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
