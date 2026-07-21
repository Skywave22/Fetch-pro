package com.fetchpro.downloadmanager.presentation.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: DownloadRepository
) : ViewModel() {
    val history: StateFlow<List<DownloadItem>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
