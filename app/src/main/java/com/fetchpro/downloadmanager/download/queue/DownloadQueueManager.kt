package com.fetchpro.downloadmanager.download.queue

import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadQueueManager @Inject constructor(
    private val dao: DownloadDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _maxConcurrent = MutableStateFlow(3)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount

    init {
        scope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _maxConcurrent.value = settings.maxConcurrent
            }
        }
    }

    fun setMaxConcurrent(count: Int) {
        val coerced = count.coerceIn(1, 10)
        _maxConcurrent.value = coerced
        scope.launch {
            settingsDataStore.updateMaxConcurrent(coerced)
        }
    }

    suspend fun canStartNewDownload(): Boolean {
        val active = dao.getDownloadsByStates(listOf(DownloadStateEntity.DOWNLOADING)).size
        _activeCount.value = active
        return active < _maxConcurrent.value
    }

    suspend fun getNextQueued(): String? {
        // earliest queued first
        val queued = dao.getDownloadsByStates(listOf(DownloadStateEntity.QUEUED))
            .sortedBy { it.createdAt }
        return queued.firstOrNull()?.id
    }

    fun notifyActiveCountChanged(count: Int) {
        _activeCount.value = count
    }

    fun enqueueAutoStart(callback: suspend () -> Unit) {
        scope.launch {
            if (canStartNewDownload()) {
                callback()
            }
        }
    }
}
