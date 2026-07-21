package com.fetchpro.downloadmanager.download.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsDataStore.settingsFlow.first()
            val keepDays = settings.keepCompletedHistoryDays
            if (keepDays <= 0) return Result.success()

            val cutoff = System.currentTimeMillis() - keepDays * 24L * 60 * 60 * 1000
            val completed = dao.getDownloadsByStates(listOf(DownloadStateEntity.COMPLETED))
            completed.filter { (it.completedAt ?: it.updatedAt) < cutoff }.forEach { entity ->
                // Optionally keep file but remove history? Here we only delete DB record if file already gone
                val file = File(entity.filePath)
                if (!file.exists()) {
                    dao.deleteDownload(entity.id)
                }
            }

            // Cleanup orphan temp files in cache
            val tempDir = File(applicationContext.cacheDir, "downloads_tmp")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { f ->
                    if (System.currentTimeMillis() - f.lastModified() > 24 * 60 * 60 * 1000) {
                        f.delete()
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
