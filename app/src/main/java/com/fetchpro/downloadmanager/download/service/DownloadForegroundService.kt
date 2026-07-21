package com.fetchpro.downloadmanager.download.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.data.local.db.toDomain
import com.fetchpro.downloadmanager.data.local.db.toEntity
import com.fetchpro.downloadmanager.domain.model.DownloadState
import com.fetchpro.downloadmanager.download.engine.MultiPartDownloader
import com.fetchpro.downloadmanager.download.queue.DownloadQueueManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.fetchpro.action.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.fetchpro.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.fetchpro.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.fetchpro.action.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        private const val FOREGROUND_NOTIF_ID = 999
    }

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var downloader: MultiPartDownloader
    @Inject lateinit var hlsDownloader: com.fetchpro.downloadmanager.download.hls.HlsDownloader
    @Inject lateinit var notificationManager: DownloadNotificationManager
    @Inject lateinit var queueManager: DownloadQueueManager
    @Inject lateinit var settingsDataStore: com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
    @Inject lateinit var httpClientProvider: com.fetchpro.downloadmanager.download.engine.HttpClientProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()
    private val activeProgress = MutableStateFlow<Map<String, Long>>(emptyMap())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Start as foreground with placeholder notification to comply with Android 14+ requirements
        val placeholder = notificationManager.buildNotification(
            item = com.fetchpro.downloadmanager.domain.model.DownloadItem(
                id = "service",
                url = "",
                fileName = "FetchPro Service",
                mimeType = null,
                totalBytes = null,
                downloadedBytes = 0,
                state = DownloadState.DOWNLOADING,
                filePath = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                completedAt = null,
                supportsRange = true,
                numParts = 1,
                errorMessage = null,
                eTag = null
            ),
            isForeground = true
        )
        startForeground(FOREGROUND_NOTIF_ID, placeholder)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)

        when (action) {
            ACTION_START -> {
                if (downloadId != null) {
                    startDownload(downloadId)
                }
            }
            ACTION_PAUSE -> {
                if (downloadId != null) pauseDownload(downloadId)
            }
            ACTION_RESUME -> {
                if (downloadId != null) startDownload(downloadId)
            }
            ACTION_CANCEL -> {
                if (downloadId != null) cancelDownload(downloadId)
            }
        }

        // If no active jobs, stop service after delay
        serviceScope.launch {
            delay(5000)
            if (activeJobs.isEmpty()) {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startDownload(downloadId: String) {
        if (activeJobs.containsKey(downloadId)) return

        val job = serviceScope.launch {
            try {
                // WiFi-only check
                val settings = settingsDataStore.settingsFlow.first()
                if (settings.wifiOnly) {
                    val isWifi = com.fetchpro.downloadmanager.download.utils.NetworkUtils.isWifiConnected(this@DownloadForegroundService)
                    if (!isWifi) {
                        val entity = downloadDao.getDownloadById(downloadId)
                        if (entity != null) {
                            downloadDao.updateDownload(
                                entity.copy(
                                    state = DownloadStateEntity.PAUSED,
                                    errorMessage = "Waiting for WiFi",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            val pausedItem = entity.toDomain().copy(state = DownloadState.PAUSED, errorMessage = "Waiting for WiFi")
                            val notifId = notificationManager.getNotificationId(downloadId)
                            notificationManager.notify(notifId, notificationManager.buildNotification(pausedItem))
                        }
                        return@launch
                    }
                }

                val entity = downloadDao.getDownloadById(downloadId) ?: return@launch
                var item = entity.toDomain()
                val parts = downloadDao.getPartsForDownload(downloadId).map { it.toDomain() }

                // Update state to DOWNLOADING
                val updatedEntity = entity.copy(
                    state = DownloadStateEntity.DOWNLOADING,
                    updatedAt = System.currentTimeMillis()
                )
                downloadDao.updateDownload(updatedEntity)
                item = updatedEntity.toDomain()
                queueManager.notifyActiveCountChanged(activeJobs.size + 1)

                // Show notification
                val notifId = notificationManager.getNotificationId(downloadId)
                notificationManager.notify(notifId, notificationManager.buildNotification(item, isForeground = false))

                // Choose downloader based on URL type - HLS support
                val downloadFlow = if (item.url.contains(".m3u8", ignoreCase = true)) {
                    hlsDownloader.downloadHls(item, httpClientProvider.client)
                } else {
                    downloader.download(item, parts)
                }

                downloadFlow
                    .collect { progress ->
                        val current = downloadDao.getDownloadById(downloadId) ?: return@collect
                        val newDownloaded = progress.downloadedBytes
                        val speed = progress.speedBps

                        // Update DB periodically
                        downloadDao.updateDownload(
                            current.copy(
                                downloadedBytes = newDownloaded,
                                updatedAt = System.currentTimeMillis(),
                                state = when (progress.state) {
                                    DownloadState.COMPLETED -> DownloadStateEntity.COMPLETED
                                    DownloadState.PAUSED -> DownloadStateEntity.PAUSED
                                    else -> DownloadStateEntity.DOWNLOADING
                                },
                                completedAt = if (progress.state == DownloadState.COMPLETED) System.currentTimeMillis() else null
                            )
                        )

                        // Update parts if any
                        if (progress.parts.isNotEmpty()) {
                            val partEntities = downloadDao.getPartsForDownload(downloadId)
                            val updatedParts = partEntities.map { pe ->
                                val matching = progress.parts.find { it.index == pe.partIndex }
                                if (matching != null) pe.copy(downloadedBytes = matching.downloadedBytes) else pe
                            }
                            updatedParts.forEach { downloadDao.updatePart(it) }
                        }

                        // Refresh domain for notification
                        val refreshedItem = downloadDao.getDownloadById(downloadId)?.toDomain() ?: item
                        val notif = notificationManager.buildNotification(refreshedItem.copy(speedBytesPerSecond = speed), speedBps = speed)
                        notificationManager.notify(notifId, notif)

                        if (progress.state == DownloadState.COMPLETED) {
                            // Integrity check if checksum provided
                            val currentEntity = downloadDao.getDownloadById(downloadId)
                            if (currentEntity?.checksum != null) {
                                val file = File(currentEntity.filePath)
                                val valid = try {
                                    com.fetchpro.downloadmanager.download.engine.IntegrityChecker.verifyChecksum(file, currentEntity.checksum!!)
                                } catch (_: Exception) { false }
                                if (!valid) {
                                    downloadDao.updateDownload(
                                        currentEntity.copy(
                                            state = DownloadStateEntity.FAILED,
                                            errorMessage = "Checksum mismatch",
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    val failedNotif = notificationManager.buildNotification(
                                        refreshedItem.copy(state = DownloadState.FAILED, errorMessage = "Checksum mismatch")
                                    )
                                    notificationManager.notify(notifId, failedNotif)
                                    return@collect
                                }
                            }

                            // Final completed notification
                            val completedNotif = notificationManager.buildNotification(
                                refreshedItem.copy(state = DownloadState.COMPLETED),
                                isForeground = false
                            )
                            notificationManager.notify(notifId, completedNotif)

                            // Try to start next queued
                            val nextId = queueManager.getNextQueued()
                            if (nextId != null) {
                                startDownload(nextId)
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // Paused or cancelled - already handled
            } catch (e: Exception) {
                val current = downloadDao.getDownloadById(downloadId)
                if (current != null) {
                    downloadDao.updateDownload(
                        current.copy(
                            state = DownloadStateEntity.FAILED,
                            errorMessage = e.message ?: "Unknown error",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    val failedItem = current.toDomain().copy(state = DownloadState.FAILED, errorMessage = e.message)
                    val notifId = notificationManager.getNotificationId(downloadId)
                    notificationManager.notify(notifId, notificationManager.buildNotification(failedItem))
                }
            } finally {
                activeJobs.remove(downloadId)
                queueManager.notifyActiveCountChanged(activeJobs.size)
                if (activeJobs.isEmpty()) {
                    // Give a moment before stopping
                    delay(2000)
                    if (activeJobs.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }

        activeJobs[downloadId] = job
    }

    private fun pauseDownload(downloadId: String) {
        serviceScope.launch {
            downloader.pause(downloadId)
            activeJobs[downloadId]?.cancelAndJoin()
            activeJobs.remove(downloadId)

            val entity = downloadDao.getDownloadById(downloadId)
            if (entity != null) {
                downloadDao.updateDownload(
                    entity.copy(
                        state = DownloadStateEntity.PAUSED,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val pausedItem = entity.toDomain().copy(state = DownloadState.PAUSED)
                val notifId = notificationManager.getNotificationId(downloadId)
                notificationManager.notify(notifId, notificationManager.buildNotification(pausedItem))
            }
        }
    }

    private fun cancelDownload(downloadId: String) {
        serviceScope.launch {
            downloader.cancel(downloadId)
            activeJobs[downloadId]?.cancelAndJoin()
            activeJobs.remove(downloadId)

            val entity = downloadDao.getDownloadById(downloadId)
            if (entity != null) {
                // Delete file
                try { File(entity.filePath).delete() } catch (_: Exception) {}
                downloadDao.deleteDownload(downloadId)
                val notifId = notificationManager.getNotificationId(downloadId)
                notificationManager.cancel(notifId)
            }

            // Start next queued
            val nextId = queueManager.getNextQueued()
            if (nextId != null) {
                startDownload(nextId)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
