package com.fetchpro.downloadmanager.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fetchpro.downloadmanager.R
import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "fetchpro_downloads"
        const val CHANNEL_NAME = "Downloads"
        const val NOTIF_ID_BASE = 1000
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_downloads_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getNotificationId(downloadId: String): Int {
        return NOTIF_ID_BASE + downloadId.hashCode().coerceIn(-100000, 100000)
    }

    fun buildNotification(
        item: DownloadItem,
        speedBps: Long = 0,
        isForeground: Boolean = false
    ): Notification {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = createActionIntent(item.id, DownloadForegroundService.ACTION_PAUSE)
        val resumeIntent = createActionIntent(item.id, DownloadForegroundService.ACTION_RESUME)
        val cancelIntent = createActionIntent(item.id, DownloadForegroundService.ACTION_CANCEL)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.fileName)
            .setContentIntent(pendingIntent)
            .setOngoing(item.state == DownloadState.DOWNLOADING)
            .setOnlyAlertOnce(true)

        when (item.state) {
            DownloadState.DOWNLOADING -> {
                val progress = item.progressPercent
                val speedText = formatSpeed(speedBps)
                builder.setContentText(
                    if (item.totalBytes != null) "$progress% • $speedText"
                    else "$speedText"
                )
                builder.setProgress(100, progress, item.totalBytes == null)
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.action_pause),
                    pauseIntent
                )
                builder.addAction(
                    android.R.drawable.ic_delete,
                    context.getString(R.string.action_cancel),
                    cancelIntent
                )
            }
            DownloadState.PAUSED -> {
                builder.setContentText("${item.progressPercent}% • Paused")
                    .setProgress(100, item.progressPercent, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.action_resume),
                    resumeIntent
                )
                builder.addAction(
                    android.R.drawable.ic_delete,
                    context.getString(R.string.action_cancel),
                    cancelIntent
                )
            }
            DownloadState.COMPLETED -> {
                builder.setContentText(context.getString(R.string.download_completed))
                    .setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .setAutoCancel(true)
            }
            DownloadState.FAILED -> {
                builder.setContentText(item.errorMessage ?: context.getString(R.string.download_failed))
                    .setProgress(100, item.progressPercent, false)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                builder.addAction(
                    android.R.drawable.ic_menu_rotate,
                    context.getString(R.string.retry),
                    resumeIntent
                )
            }
            else -> {
                builder.setContentText(item.state.name)
                    .setProgress(0, 0, true)
            }
        }

        if (isForeground) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun createActionIntent(downloadId: String, action: String): PendingIntent {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            this.action = action
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getService(
            context,
            (downloadId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notify(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }

    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    private fun formatSpeed(bps: Long): String {
        if (bps <= 0) return "0 B/s"
        val kb = bps / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.1f MB/s", mb)
            kb >= 1 -> String.format("%.0f KB/s", kb)
            else -> "$bps B/s"
        }
    }
}
