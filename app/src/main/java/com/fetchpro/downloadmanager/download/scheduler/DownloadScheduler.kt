package com.fetchpro.downloadmanager.download.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.fetchpro.downloadmanager.broadcast.DownloadBroadcastReceiver
import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.download.service.DownloadForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Download Scheduler - 1DM+ feature
 * Allows scheduling downloads for future time, using AlarmManager + WorkManager
 */
@Singleton
class DownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao
) {

    companion object {
        const val ACTION_SCHEDULED_DOWNLOAD = "com.fetchpro.action.SCHEDULED_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "extra_scheduled_download_id"
    }

    /**
     * Schedule a download to start at specific time
     * @param downloadId ID of download to schedule
     * @param triggerAtMillis epoch millis when download should start
     */
    fun scheduleDownload(downloadId: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, DownloadBroadcastReceiver::class.java).apply {
            action = ACTION_SCHEDULED_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use exact alarm if possible, otherwise inexact
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Fallback to inexact if exact alarms not permitted (Android 12+)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        // Also update DB to mark as scheduled
        // We'll use QUEUED state but with a separate scheduled flag in future
    }

    /**
     * Schedule using WorkManager for more reliability (15 min minimum)
     */
    fun scheduleWithWorkManager(downloadId: String, delayMinutes: Long) {
        val data = Data.Builder()
            .putString(EXTRA_DOWNLOAD_ID, downloadId)
            .build()

        val request = OneTimeWorkRequestBuilder<ScheduledDownloadWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(data)
            .addTag("scheduled_$downloadId")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Cancel scheduled download
     */
    fun cancelScheduled(downloadId: String) {
        val intent = Intent(context, DownloadBroadcastReceiver::class.java).apply {
            action = ACTION_SCHEDULED_DOWNLOAD
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        WorkManager.getInstance(context).cancelAllWorkByTag("scheduled_$downloadId")
    }

    /**
     * Trigger a scheduled download now (called by receiver)
     */
    suspend fun triggerScheduled(downloadId: String) {
        val entity = dao.getDownloadById(downloadId) ?: return
        if (entity.state == DownloadStateEntity.QUEUED || entity.state == DownloadStateEntity.PAUSED) {
            val serviceIntent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_START
                putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
