package com.fetchpro.downloadmanager.data.local

import android.content.Context
import android.net.Uri
import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.toDomain
import com.fetchpro.downloadmanager.data.local.db.toEntity
import com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
import com.fetchpro.downloadmanager.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val downloads: List<BackupDownloadItem>,
    val settings: AppSettings,
    val exportNote: String = "FetchPro Backup - ADM feature"
)

@Serializable
data class BackupDownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val state: String,
    val filePath: String,
    val createdAt: Long,
    val supportsRange: Boolean
)

/**
 * Backup/restore downloads & settings - ADM feature
 * Export to JSON via SAF
 */
@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val settingsDataStore: SettingsDataStore
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun createBackup(): BackupData {
        val downloads = dao.observeAllDownloads().first().map { entity ->
            BackupDownloadItem(
                id = entity.id,
                url = entity.url,
                fileName = entity.fileName,
                mimeType = entity.mimeType,
                totalBytes = entity.totalBytes,
                downloadedBytes = entity.downloadedBytes,
                state = entity.state.name,
                filePath = entity.filePath,
                createdAt = entity.createdAt,
                supportsRange = entity.supportsRange
            )
        }
        val settings = settingsDataStore.settingsFlow.first()

        return BackupData(
            downloads = downloads,
            settings = settings
        )
    }

    suspend fun exportBackupToUri(uri: Uri): Boolean {
        return try {
            val backup = createBackup()
            val jsonString = json.encodeToString(backup)
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(jsonString.toByteArray())
                output.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackupFromUri(uri: Uri): BackupData? {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: return null

            json.decodeFromString<BackupData>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun restoreDownloads(backup: BackupData, restoreFiles: Boolean = false): Int {
        var restored = 0
        try {
            for (item in backup.downloads) {
                // Only restore if not already exists
                val existing = dao.getDownloadById(item.id)
                if (existing == null) {
                    // Create entity with QUEUED state for re-download, or PAUSED if file exists
                    val entity = com.fetchpro.downloadmanager.data.local.db.DownloadEntity(
                        id = item.id,
                        url = item.url,
                        fileName = item.fileName,
                        mimeType = item.mimeType,
                        totalBytes = item.totalBytes,
                        downloadedBytes = if (restoreFiles) item.downloadedBytes else 0,
                        state = com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity.QUEUED,
                        filePath = item.filePath,
                        createdAt = item.createdAt,
                        updatedAt = System.currentTimeMillis(),
                        completedAt = null,
                        supportsRange = item.supportsRange,
                        numParts = 8,
                        errorMessage = null,
                        eTag = null,
                        headersJson = "{}",
                        checksum = null
                    )
                    dao.insertDownload(entity)
                    restored++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return restored
    }

    suspend fun restoreSettings(backup: BackupData) {
        try {
            val settings = backup.settings
            settingsDataStore.updateMaxConcurrent(settings.maxConcurrent)
            settingsDataStore.updateMaxParts(settings.maxParts)
            settingsDataStore.setWifiOnly(settings.wifiOnly)
            settingsDataStore.setAutoRetry(settings.autoRetry)
            settingsDataStore.setKeepHistoryDays(settings.keepCompletedHistoryDays)
            settingsDataStore.setGlobalSpeedLimit(settings.globalSpeedLimitBps)
            settingsDataStore.setUnlimitedRetry(settings.unlimitedRetry)
            // ... add more settings as needed
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
