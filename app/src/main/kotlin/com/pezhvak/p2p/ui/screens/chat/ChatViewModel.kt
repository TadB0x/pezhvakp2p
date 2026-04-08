package com.pezhvak.p2p.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.core.crypto.sha256
import com.pezhvak.p2p.core.crypto.toHexString
import com.pezhvak.p2p.core.db.dao.ConversationDao
import com.pezhvak.p2p.core.db.dao.MessageDao
import com.pezhvak.p2p.core.db.entities.*
import com.pezhvak.p2p.core.identity.KeyManager
import com.pezhvak.p2p.transport.TransportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversationName: String = "",
    val myPubKey: String = "",
    val transportIndicator: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val transportManager: TransportManager,
    private val keyManager: KeyManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var _conversationId = MutableStateFlow("")
    val messages = _conversationId
        .filter { it.isNotEmpty() }
        .flatMapLatest { messageDao.observeMessages(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        val identity = keyManager.getOrCreateIdentity()
        _uiState.update { it.copy(myPubKey = identity.pubKeyHex) }
    }

    fun loadConversation(conversationId: String) {
        _conversationId.value = conversationId
        viewModelScope.launch {
            conversationDao.observe(conversationId).filterNotNull().collect { conv ->
                _uiState.update { state ->
                    state.copy(
                        conversationName = conv.name,
                        transportIndicator = null  // updated dynamically
                    )
                }
                conversationDao.markRead(conversationId)
            }
        }
        // Listen for incoming messages to this conversation
        viewModelScope.launch {
            transportManager.messages
                .filter { it.tags.any { t -> t.getOrNull(0) == "p" && t.getOrNull(1) == keyManager.getOrCreateIdentity().pubKeyHex } }
                .collect { incoming ->
                    val entity = MessageEntity(
                        eventId = incoming.eventId,
                        conversationId = conversationId,
                        conversationType = ConversationType.DM,
                        senderPubKey = incoming.senderPubKey,
                        senderSig = "",
                        contentEncrypted = incoming.encryptedContent,
                        contentPlain = null, // decrypted in message repository
                        contentHash = sha256(incoming.encryptedContent.toByteArray()).toHexString(),
                        replyToEventId = null,
                        mediaAttachments = null,
                        reactions = null,
                        createdAt = incoming.createdAt,
                        receivedAt = System.currentTimeMillis(),
                        deliveryStatus = DeliveryStatus.DELIVERED,
                        transportSource = incoming.transport,
                        seenByPeerAt = null,
                    )
                    messageDao.insert(entity)
                }
        }
    }

    fun sendMessage(content: String) {
        viewModelScope.launch {
            val myId = keyManager.getOrCreateIdentity()
            val convId = _conversationId.value
            val eventId = viewModelScope.run {
                transportManager.sendDirectMessage(
                    recipientPubKeyHex = convId,  // for DMs, convId is recipient pubkey
                    content = content,
                )
            }
            // Optimistically insert sent message
            val entity = MessageEntity(
                eventId = eventId,
                conversationId = convId,
                conversationType = ConversationType.DM,
                senderPubKey = myId.pubKeyHex,
                senderSig = "",
                contentEncrypted = null,
                contentPlain = content,
                contentHash = sha256(content.toByteArray()).toHexString(),
                replyToEventId = null,
                mediaAttachments = null,
                reactions = null,
                createdAt = System.currentTimeMillis(),
                receivedAt = System.currentTimeMillis(),
                deliveryStatus = DeliveryStatus.SENDING,
                transportSource = TransportSource.NOSTR,
                seenByPeerAt = null,
            )
            messageDao.insert(entity)

            // Update conversation
            conversationDao.updateLastMessage(convId, System.currentTimeMillis(),
                content.take(50))
        }
    }
}
