package com.fetchpro.downloadmanager.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fetchpro.downloadmanager.data.local.db.DownloadDatabase
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.download.service.DownloadForegroundService
import com.fetchpro.downloadmanager.download.worker.CleanupWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class DownloadBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var database: DownloadDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                scheduleCleanup(context)
            }
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                scope.launch {
                    val dao = database.downloadDao()
                    val paused = dao.getDownloadsByStates(listOf(DownloadStateEntity.PAUSED))
                }
            }
            com.fetchpro.downloadmanager.download.scheduler.DownloadScheduler.ACTION_SCHEDULED_DOWNLOAD -> {
                val downloadId = intent.getStringExtra(com.fetchpro.downloadmanager.download.scheduler.DownloadScheduler.EXTRA_DOWNLOAD_ID)
                if (downloadId != null) {
                    scope.launch {
                        try {
                            val scheduler = com.fetchpro.downloadmanager.download.scheduler.DownloadScheduler(context, database.downloadDao())
                            scheduler.triggerScheduled(downloadId)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    companion object {
        fun scheduleCleanup(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "fetchpro_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerResumeIfNetwork(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_RESUME
                putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }
    }
}
