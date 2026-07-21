package com.fetchpro.downloadmanager.presentation.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadPart
import com.fetchpro.downloadmanager.domain.repository.DownloadRepository
import com.fetchpro.downloadmanager.domain.usecase.*
import com.fetchpro.downloadmanager.download.limiter.SpeedLimiterManager
import com.fetchpro.downloadmanager.download.scheduler.DownloadScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val item: DownloadItem? = null,
    val parts: List<DownloadPart> = emptyList(),
    val isLoading: Boolean = true,
    val speedLimitKbps: Long = 0
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val pause: PauseDownloadUseCase,
    private val resume: ResumeDownloadUseCase,
    private val cancel: CancelDownloadUseCase,
    private val retry: RetryDownloadUseCase,
    private val scheduler: DownloadScheduler,
    private val speedLimiterManager: SpeedLimiterManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val downloadId: String = savedStateHandle.get<String>("downloadId") ?: ""

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeById(downloadId).collect { item ->
                _uiState.update { it.copy(item = item, isLoading = false) }
            }
        }
        viewModelScope.launch {
            while (true) {
                val parts = repository.getParts(downloadId)
                val limit = speedLimiterManager.getPerDownloadLimit(downloadId)
                _uiState.update { it.copy(parts = parts, speedLimitKbps = if (limit == 0L) 0 else limit / 1024) }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun pause() = viewModelScope.launch { pause(downloadId) }
    fun resume() = viewModelScope.launch { resume(downloadId) }
    fun cancel() = viewModelScope.launch { cancel(downloadId) }
    fun retry() = viewModelScope.launch { retry(downloadId) }

    fun setSpeedLimit(kbps: Long) {
        val bps = if (kbps <= 0) 0 else kbps * 1024
        speedLimiterManager.setPerDownloadLimit(downloadId, bps)
        _uiState.update { it.copy(speedLimitKbps = kbps) }
    }

    fun scheduleInMinutes(minutes: Long) {
        viewModelScope.launch {
            val triggerAt = System.currentTimeMillis() + minutes * 60 * 1000
            scheduler.scheduleDownload(downloadId, triggerAt)
        }
    }

    fun scheduleWithWorkManager(delayMinutes: Long) {
        scheduler.scheduleWithWorkManager(downloadId, delayMinutes)
    }

    fun cancelSchedule() {
        scheduler.cancelScheduled(downloadId)
    }
}
