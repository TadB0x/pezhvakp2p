package com.pezhvak.p2p.ui.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pezhvak.p2p.news.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
) : ViewModel() {
    val newsItems = newsRepository.newsFlow
    fun markRead(id: String) = viewModelScope.launch { newsRepository.markRead(id) }
    fun setSaved(id: String, saved: Boolean) = viewModelScope.launch { newsRepository.setSaved(id, saved) }
}
