package com.pezhvak.p2p.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.core.db.dao.ContactDao
import com.pezhvak.p2p.core.db.dao.ConversationDao
import com.pezhvak.p2p.core.db.entities.ConversationEntity
import com.pezhvak.p2p.core.db.entities.ConversationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val conversationDao: ConversationDao,
) : ViewModel() {
    val contacts = contactDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun ensureConversation(pubKey: String, name: String) = viewModelScope.launch {
        val existing = conversationDao.observe(pubKey)
        // Conversation ID for DMs = recipient's pubkey
        conversationDao.upsert(
            ConversationEntity(
                id = pubKey,
                type = ConversationType.DM,
                name = name,
                description = null,
                avatarUrl = null,
                participantPubKeys = """["$pubKey"]""",
                encryptedGroupKey = null,
                creatorPubKey = pubKey,
                createdAt = System.currentTimeMillis(),
                lastMessageAt = System.currentTimeMillis(),
                lastMessagePreview = null,
                unreadCount = 0,
                isMuted = false,
                isPinned = false,
                isArchived = false,
                nostrRelayHints = null,
            )
        )
    }
}
