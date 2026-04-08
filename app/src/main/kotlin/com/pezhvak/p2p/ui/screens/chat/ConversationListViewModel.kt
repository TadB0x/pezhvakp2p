package com.pezhvak.p2p.ui.screens.chat

import androidx.lifecycle.ViewModel
import com.pezhvak.p2p.core.db.dao.ConversationDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    conversationDao: ConversationDao,
) : ViewModel() {
    val conversations = conversationDao.observeAll()
}
