package com.fetchpro.downloadmanager.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fetchpro.downloadmanager.MainActivity
import com.fetchpro.downloadmanager.R
import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.download.service.DownloadForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home screen widget - ADM feature: widget on home screen showing progress
 */
@AndroidEntryPoint
class DownloadWidget : AppWidgetProvider() {

    @Inject lateinit var downloadDao: DownloadDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        scope.launch {
            val activeDownloads = try {
                downloadDao.getDownloadsByStates(listOf(DownloadStateEntity.DOWNLOADING, DownloadStateEntity.QUEUED))
            } catch (_: Exception) {
                emptyList()
            }

            val views = RemoteViews(context.packageName, R.layout.widget_download).apply {
                if (activeDownloads.isEmpty()) {
                    setTextViewText(R.id.widget_title, "No active downloads")
                    setTextViewText(R.id.widget_progress, "0%")
                    setProgressBar(R.id.widget_progress_bar, 100, 0, false)
                } else {
                    val first = activeDownloads.first()
                    val progress = if (first.totalBytes != null && first.totalBytes > 0) {
                        (first.downloadedBytes * 100 / first.totalBytes).toInt()
                    } else 0
                    setTextViewText(R.id.widget_title, first.fileName)
                    setTextViewText(R.id.widget_progress, "$progress% • ${activeDownloads.size} active")
                    setProgressBar(R.id.widget_progress_bar, 100, progress, false)
                }

                // Open app intent
                val openIntent = Intent(context, MainActivity::class.java).let { intent ->
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                }
                setOnClickPendingIntent(R.id.widget_container, openIntent)

                // Pause all intent
                val pauseIntent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = "com.fetchpro.action.PAUSE_ALL_WIDGET"
                }.let { intent ->
                    PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                }
                setOnClickPendingIntent(R.id.widget_pause_all, pauseIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
