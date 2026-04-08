package com.pezhvak.p2p.ui.screens.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.core.db.dao.ChannelDao
import com.pezhvak.p2p.core.db.dao.MessageDao
import com.pezhvak.p2p.transport.TransportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val messageDao: MessageDao,
    private val transportManager: TransportManager,
) : ViewModel() {
    val subscribedChannels = channelDao.observeSubscribed()
    val allChannels = channelDao.observeAll()

    fun channelMessages(channelId: String) = messageDao.observeMessages(channelId)

    fun subscribe(channelId: String) = viewModelScope.launch {
        channelDao.setSubscribed(channelId, true)
        transportManager.subscribeToChannel(channelId)
    }

    fun postToChannel(channelId: String, content: String) = viewModelScope.launch {
        transportManager.broadcastChannelMessage(channelId, content)
    }
}
