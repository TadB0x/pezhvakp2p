package com.pezhvak.p2p.ui.screens.forums

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.core.crypto.sha256
import com.pezhvak.p2p.core.crypto.toHexString
import com.pezhvak.p2p.core.db.dao.ForumDao
import com.pezhvak.p2p.core.db.dao.MessageDao
import com.pezhvak.p2p.core.db.entities.*
import com.pezhvak.p2p.core.identity.KeyManager
import com.pezhvak.p2p.transport.nostr.NostrEvent
import com.pezhvak.p2p.transport.nostr.NostrKind
import com.pezhvak.p2p.transport.nostr.NostrRelayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun CreateThreadScreen(
    forumId: String,
    onBack: () -> Unit,
    onCreated: (threadId: String) -> Unit,
    viewModel: CreateThreadViewModel = hiltViewModel(),
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = { Text("New Thread", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Thread Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Post content") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 6,
            )
            Button(
                onClick = {
                    if (title.isNotBlank() && body.isNotBlank()) {
                        isPosting = true
                        viewModel.createThread(forumId, title, body) { id ->
                            onCreated(id)
                        }
                    }
                },
                enabled = title.isNotBlank() && body.isNotBlank() && !isPosting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isPosting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Post Thread")
                }
            }
        }
    }
}

@HiltViewModel
class CreateThreadViewModel @Inject constructor(
    private val forumDao: ForumDao,
    private val messageDao: MessageDao,
    private val keyManager: KeyManager,
    private val nostrRelayManager: NostrRelayManager,
) : ViewModel() {

    fun createThread(forumId: String, title: String, body: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val myKey = keyManager.getOrCreateIdentity().pubKeyHex
            val event = NostrEvent.build(
                privKeyHex = keyManager.getPrivateKeyHex(),
                kind = NostrKind.FORUM_THREAD,
                content = body,
                tags = listOf(listOf("subject", title), listOf("e", forumId, "", "root")),
            )
            nostrRelayManager.publish(event)

            forumDao.upsertThread(ForumThreadEntity(
                id = event.id,
                forumId = forumId,
                title = title,
                authorPubKey = myKey,
                rootMessageId = event.id,
                replyCount = 0,
                lastReplyAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                tags = null,
                isLocked = false,
                isPinned = false,
            ))
            messageDao.insert(MessageEntity(
                eventId = event.id,
                conversationId = event.id,
                conversationType = ConversationType.FORUM,
                senderPubKey = myKey,
                senderSig = event.sig,
                contentEncrypted = null,
                contentPlain = body,
                contentHash = sha256(body.toByteArray()).toHexString(),
                replyToEventId = null,
                mediaAttachments = null,
                reactions = null,
                createdAt = System.currentTimeMillis(),
                receivedAt = System.currentTimeMillis(),
                deliveryStatus = DeliveryStatus.SENT,
                transportSource = TransportSource.NOSTR,
                seenByPeerAt = null,
                threadId = event.id,
            ))
            onCreated(event.id)
        }
    }
}
