package com.fetchpro.downloadmanager.download.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduledDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: DownloadScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(DownloadScheduler.EXTRA_DOWNLOAD_ID) ?: return Result.failure()
        return try {
            scheduler.triggerScheduled(downloadId)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
