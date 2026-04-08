package com.pezhvak.p2p.ui.screens.forums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.core.db.dao.ForumDao
import com.pezhvak.p2p.core.db.dao.MessageDao
import com.pezhvak.p2p.transport.TransportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForumViewModel @Inject constructor(
    private val forumDao: ForumDao,
    private val messageDao: MessageDao,
    private val transportManager: TransportManager,
) : ViewModel() {
    val threads = forumDao.observeThreads("global")  // default forum

    fun threadMessages(threadId: String) = messageDao.observeThread(threadId)

    fun postReply(threadId: String, content: String) = viewModelScope.launch {
        transportManager.broadcastChannelMessage(
            channelId = threadId,
            content = content,
            kind = com.pezhvak.p2p.transport.nostr.NostrKind.FORUM_REPLY
        )
        forumDao.incrementReply(threadId, System.currentTimeMillis())
    }
}
