package com.fetchpro.downloadmanager.presentation.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.data.local.LinkImportExportManager
import com.fetchpro.downloadmanager.domain.model.DownloadCategory
import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadSortBy
import com.fetchpro.downloadmanager.domain.model.DownloadTimeFilter
import com.fetchpro.downloadmanager.domain.usecase.*
import com.fetchpro.downloadmanager.download.service.SmartClipboardMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val filteredDownloads: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val urlInput: String = "",
    val isAdding: Boolean = false,
    val sortBy: DownloadSortBy = DownloadSortBy.DATE_NEWEST,
    val category: DownloadCategory = DownloadCategory.ALL,
    val timeFilter: DownloadTimeFilter = DownloadTimeFilter.ALL,
    val smartClipboardEnabled: Boolean = false,
    val clipboardDetectedUrl: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeDownloads: ObserveDownloadsUseCase,
    private val createDownload: CreateDownloadUseCase,
    private val pauseDownload: PauseDownloadUseCase,
    private val resumeDownload: ResumeDownloadUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val deleteDownload: DeleteDownloadUseCase,
    private val retryDownload: RetryDownloadUseCase,
    private val queueDownload: QueueDownloadUseCase,
    private val pauseAllUseCase: PauseAllDownloadsUseCase,
    private val resumeAllUseCase: ResumeAllDownloadsUseCase,
    private val cancelAllUseCase: CancelAllDownloadsUseCase,
    private val deleteCompletedUseCase: DeleteCompletedDownloadsUseCase,
    private val deleteAllUseCase: DeleteAllDownloadsUseCase,
    private val refreshLinkUseCase: RefreshExpiredLinkUseCase,
    private val linkManager: LinkImportExportManager,
    private val clipboardMonitor: SmartClipboardMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allDownloads: List<DownloadItem> = emptyList()

    init {
        observeAll()
        observeClipboard()
    }

    private fun observeAll() {
        viewModelScope.launch {
            observeDownloads()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { list ->
                    allDownloads = list
                    applyFilters()
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun observeClipboard() {
        viewModelScope.launch {
            clipboardMonitor.observeClipboard().collect { url ->
                _uiState.update { state ->
                    if (state.smartClipboardEnabled) {
                        state.copy(clipboardDetectedUrl = url)
                    } else state
                }
            }
        }
    }

    private fun applyFilters() {
        var filtered = allDownloads
        // Category
        if (_uiState.value.category != DownloadCategory.ALL) {
            filtered = filtered.filter {
                DownloadCategory.fromMimeType(it.mimeType, it.fileName) == _uiState.value.category
            }
        }
        // Time
        filtered = _uiState.value.timeFilter.filter(filtered)
        // Sort
        filtered = _uiState.value.sortBy.sort(filtered)

        _uiState.update { it.copy(downloads = allDownloads, filteredDownloads = filtered) }
    }

    fun onUrlInputChanged(value: String) {
        _uiState.update { it.copy(urlInput = value) }
    }

    fun setSort(sort: DownloadSortBy) {
        _uiState.update { it.copy(sortBy = sort) }
        applyFilters()
    }

    fun setCategory(cat: DownloadCategory) {
        _uiState.update { it.copy(category = cat) }
        applyFilters()
    }

    fun setTimeFilter(filter: DownloadTimeFilter) {
        _uiState.update { it.copy(timeFilter = filter) }
        applyFilters()
    }

    fun setSmartClipboard(enabled: Boolean) {
        _uiState.update { it.copy(smartClipboardEnabled = enabled) }
    }

    fun consumeClipboardUrl() {
        val url = _uiState.value.clipboardDetectedUrl ?: return
        _uiState.update { it.copy(urlInput = url, clipboardDetectedUrl = null) }
        addDownload()
    }

    fun dismissClipboard() {
        _uiState.update { it.copy(clipboardDetectedUrl = null) }
    }

    fun addDownload() {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank() || !url.startsWith("http")) {
            _uiState.update { it.copy(error = "Enter a valid http/https URL") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true, error = null) }
            try {
                val item = createDownload(url = url)
                queueDownload(item.id)
                _uiState.update { it.copy(urlInput = "", isAdding = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add", isAdding = false) }
            }
        }
    }

    fun addMultipleDownloads(urls: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true) }
            var added = 0
            var failed = 0
            for (url in urls) {
                try {
                    val item = createDownload(url = url)
                    queueDownload(item.id)
                    added++
                } catch (_: Exception) {
                    failed++
                }
            }
            _uiState.update {
                it.copy(
                    isAdding = false,
                    error = if (failed > 0) "Added $added, failed $failed" else null
                )
            }
        }
    }

    fun pause(id: String) = viewModelScope.launch { pauseDownload(id) }
    fun resume(id: String) = viewModelScope.launch { resumeDownload(id) }
    fun cancel(id: String) = viewModelScope.launch { cancelDownload(id) }
    fun delete(id: String) = viewModelScope.launch { deleteDownload(id) }
    fun retry(id: String) = viewModelScope.launch { retryDownload(id) }

    fun pauseAll() = viewModelScope.launch {
        try { pauseAllUseCase() } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
    }

    fun resumeAll() = viewModelScope.launch {
        try { resumeAllUseCase() } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
    }

    fun cancelAll() = viewModelScope.launch {
        try { cancelAllUseCase() } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
    }

    fun deleteCompleted() = viewModelScope.launch {
        try { deleteCompletedUseCase() } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
    }

    fun deleteAll() = viewModelScope.launch {
        try { deleteAllUseCase() } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
    }

    fun refreshLink(id: String) = viewModelScope.launch {
        try {
            val result = refreshLinkUseCase(id)
            _uiState.update { it.copy(error = result.message) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Refresh failed: ${e.message}") }
        }
    }

    fun importFromUri(uri: Uri) = viewModelScope.launch {
        try {
            val urls = linkManager.importLinksFromUri(uri)
            if (urls.isEmpty()) {
                _uiState.update { it.copy(error = "No URLs found in file") }
            } else {
                addMultipleDownloads(urls)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Import failed: ${e.message}") }
        }
    }

    fun exportToUri(uri: Uri) = viewModelScope.launch {
        try {
            val links = allDownloads.map { it.url }.distinct()
            val success = linkManager.exportLinksToUri(uri, links)
            _uiState.update {
                it.copy(error = if (success) "Exported ${links.size} links" else "Export failed")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Export failed: ${e.message}") }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
