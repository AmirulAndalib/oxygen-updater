package com.oxygenupdater.ui.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxygenupdater.models.Article
import com.oxygenupdater.repositories.ServerRepository
import com.oxygenupdater.ui.RefreshAwareState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArticleViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val refreshingFlow = MutableStateFlow(true)
    private val flow = MutableStateFlow(item)

    val state = refreshingFlow.combine(flow) { refreshing, item ->
        RefreshAwareState(refreshing, item)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RefreshAwareState(refreshingFlow.value, flow.value)
    )

    fun refreshItem(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        refreshingFlow.value = true
        flow.emit(serverRepository.fetchArticle(id).also { item = it })
        refreshingFlow.value = false
    }

    fun clearItem() {
        flow.value = null
        item = null
    }

    companion object {
        private const val TAG = "ArticleViewModel"

        var item by mutableStateOf<Article?>(null)
    }
}
