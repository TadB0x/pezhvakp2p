package com.pezhvak.p2p.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pezhvak.p2p.core.db.entities.MessageEntity

@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onCallClick: (Boolean) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) { viewModel.loadConversation(conversationId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = {
                    Column {
                        Text(uiState.conversationName, fontWeight = FontWeight.SemiBold)
                        uiState.transportIndicator?.let { transport ->
                            Text(transport,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onCallClick(false) }) {
                        Icon(Icons.Default.Call, "Voice call")
                    }
                    IconButton(onClick = { onCallClick(true) }) {
                        Icon(Icons.Default.Videocam, "Video call")
                    }
                    IconButton(onClick = { /* more options */ }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                value = messageInput,
                onValueChange = { messageInput = it },
                onSend = {
                    if (messageInput.isNotBlank()) {
                        viewModel.sendMessage(messageInput)
                        messageInput = ""
                    }
                },
                onAttach = { /* file picker */ },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            reverseLayout = false,
        ) {
            items(messages, key = { it.eventId }) { message ->
                MessageBubble(
                    message = message,
                    isOwn = message.senderPubKey == uiState.myPubKey,
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity, isOwn: Boolean) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOwn)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isOwn) 16.dp else 4.dp,
                    bottomEnd = if (isOwn) 4.dp else 16.dp,
                ))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = message.contentPlain ?: "…",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f),
                    )
                    if (isOwn) {
                        DeliveryStatusIcon(message.deliveryStatus, textColor)
                    }
                    // Transport source indicator
                    TransportDot(message.transportSource)
                }
            }
        }
    }
}

@Composable
fun DeliveryStatusIcon(
    status: com.pezhvak.p2p.core.db.entities.DeliveryStatus,
    tint: Color,
) {
    val icon = when (status) {
        com.pezhvak.p2p.core.db.entities.DeliveryStatus.SENDING -> Icons.Default.AccessTime
        com.pezhvak.p2p.core.db.entities.DeliveryStatus.SENT -> Icons.Default.Check
        com.pezhvak.p2p.core.db.entities.DeliveryStatus.DELIVERED -> Icons.Default.DoneAll
        com.pezhvak.p2p.core.db.entities.DeliveryStatus.SEEN -> Icons.Default.DoneAll
        com.pezhvak.p2p.core.db.entities.DeliveryStatus.FAILED -> Icons.Default.Error
    }
    val color = if (status == com.pezhvak.p2p.core.db.entities.DeliveryStatus.SEEN)
        Color(0xFF4EABF7) else tint.copy(alpha = 0.6f)
    Icon(icon, null, modifier = Modifier.size(12.dp), tint = color)
}

@Composable
fun TransportDot(source: com.pezhvak.p2p.core.db.entities.TransportSource) {
    val (color, label) = when (source) {
        com.pezhvak.p2p.core.db.entities.TransportSource.NOSTR -> Color(0xFF7B52FF) to "N"
        com.pezhvak.p2p.core.db.entities.TransportSource.BLE_MESH -> Color(0xFF00C6A2) to "B"
        com.pezhvak.p2p.core.db.entities.TransportSource.WIFI_DIRECT -> Color(0xFF4EABF7) to "W"
        else -> Color.Transparent to ""
    }
    if (label.isNotEmpty()) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

@Composable
fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, "Attach")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            if (value.isNotBlank()) {
                FilledIconButton(onClick = onSend) {
                    Icon(Icons.Default.Send, "Send")
                }
            } else {
                IconButton(onClick = { /* voice message */ }) {
                    Icon(Icons.Default.Mic, "Voice")
                }
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val date = java.util.Date(ms)
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}
