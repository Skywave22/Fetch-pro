package com.fetchpro.downloadmanager.presentation.ui.screens.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.data.repository.BrowserRepository
import com.fetchpro.downloadmanager.domain.model.Bookmark
import com.fetchpro.downloadmanager.domain.model.BrowserHistoryEntry
import com.fetchpro.downloadmanager.domain.model.BrowserTab
import com.fetchpro.downloadmanager.domain.usecase.CreateDownloadUseCase
import com.fetchpro.downloadmanager.domain.usecase.QueueDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab(id = UUID.randomUUID().toString(), url = "https://www.google.com")),
    val currentTabId: String? = null,
    val history: List<BrowserHistoryEntry> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val detectedLinks: List<String> = emptyList(), // auto-catch MUSIC/VIDEO
    val isIncognito: Boolean = false
) {
    val currentTab: BrowserTab?
        get() = tabs.find { it.id == currentTabId } ?: tabs.firstOrNull()
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val browserRepository: BrowserRepository,
    private val createDownload: CreateDownloadUseCase,
    private val queueDownload: QueueDownloadUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            browserRepository.observeHistory().collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
        viewModelScope.launch {
            browserRepository.observeBookmarks().collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
        // Set first tab as current
        _uiState.update { it.copy(currentTabId = it.tabs.firstOrNull()?.id) }
    }

    fun newTab(url: String = "https://www.google.com", incognito: Boolean = false) {
        val tab = BrowserTab(id = UUID.randomUUID().toString(), url = url, isIncognito = incognito)
        _uiState.update { state ->
            state.copy(tabs = state.tabs + tab, currentTabId = tab.id, isIncognito = incognito)
        }
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val newTabs = state.tabs.filterNot { it.id == tabId }
            if (newTabs.isEmpty()) {
                val newTab = BrowserTab(id = UUID.randomUUID().toString(), url = "https://www.google.com")
                state.copy(tabs = listOf(newTab), currentTabId = newTab.id)
            } else {
                val newCurrent = if (state.currentTabId == tabId) newTabs.last().id else state.currentTabId
                state.copy(tabs = newTabs, currentTabId = newCurrent)
            }
        }
    }

    fun selectTab(tabId: String) {
        _uiState.update { it.copy(currentTabId = tabId) }
        val tab = _uiState.value.tabs.find { it.id == tabId }
        _uiState.update { it.copy(isIncognito = tab?.isIncognito ?: false) }
    }

    fun navigateCurrentTab(url: String) {
        val currentId = _uiState.value.currentTabId ?: return
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { if (it.id == currentId) it.copy(url = url) else it }
            state.copy(tabs = updatedTabs)
        }
    }

    fun onPageFinished(url: String, title: String?) {
        viewModelScope.launch {
            if (!_uiState.value.isIncognito) {
                browserRepository.addHistory(url, title)
            }
        }
    }

    fun toggleBookmark() {
        val current = _uiState.value.currentTab ?: return
        viewModelScope.launch {
            browserRepository.toggleBookmark(current.url, current.title)
        }
    }

    fun clearHistory() = viewModelScope.launch {
        browserRepository.clearHistory()
    }

    /**
     * Auto-catch MUSIC/VIDEO links from page - 1DM+ feature
     * This is called from WebView when it detects downloadable links via JS or shouldIntercept
     */
    fun onLinksDetected(links: List<String>) {
        // Filter for media
        val mediaLinks = links.filter { url ->
            val lower = url.lowercase()
            lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".mkv") ||
                    lower.endsWith(".webm") || lower.endsWith(".m4a") || lower.endsWith(".flac") ||
                    lower.contains(".m3u8") // HLS
        }
        if (mediaLinks.isNotEmpty()) {
            _uiState.update { it.copy(detectedLinks = (it.detectedLinks + mediaLinks).distinct()) }
        }
    }

    fun downloadLink(url: String) {
        viewModelScope.launch {
            try {
                val item = createDownload(url = url)
                queueDownload(item.id)
            } catch (_: Exception) {}
        }
    }

    fun downloadAllDetected() {
        val links = _uiState.value.detectedLinks
        viewModelScope.launch {
            links.forEach { url ->
                try {
                    val item = createDownload(url = url)
                    queueDownload(item.id)
                } catch (_: Exception) {}
            }
            _uiState.update { it.copy(detectedLinks = emptyList()) }
        }
    }

    fun clearDetected() {
        _uiState.update { it.copy(detectedLinks = emptyList()) }
    }
}
