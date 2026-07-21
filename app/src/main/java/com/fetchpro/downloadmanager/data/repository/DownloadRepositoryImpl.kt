package com.fetchpro.downloadmanager.data.repository

import android.content.Context
import android.content.Intent
import com.fetchpro.downloadmanager.data.local.db.*
import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadPart
import com.fetchpro.downloadmanager.domain.model.DownloadState
import com.fetchpro.downloadmanager.domain.repository.DownloadRepository
import com.fetchpro.downloadmanager.download.engine.MultiPartDownloader
import com.fetchpro.downloadmanager.download.service.DownloadForegroundService
import com.fetchpro.downloadmanager.download.utils.FileUtils
import com.fetchpro.downloadmanager.download.utils.MimeTypeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val downloader: MultiPartDownloader,
    private val settingsDataStore: com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
) : DownloadRepository {

    override fun observeAll(): Flow<List<DownloadItem>> {
        return dao.observeAllDownloads().map { list -> list.map { it.toDomain() } }
    }

    override fun observeById(id: String): Flow<DownloadItem?> {
        return dao.observeDownloadById(id).map { it?.toDomain() }
    }

    override fun observeHistory(): Flow<List<DownloadItem>> {
        return dao.observeHistory().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getById(id: String): DownloadItem? {
        return dao.getDownloadById(id)?.toDomain()
    }

    override suspend fun getParts(downloadId: String): List<DownloadPart> {
        return dao.getPartsForDownload(downloadId).map { it.toDomain() }
    }

    override suspend fun createDownload(
        url: String,
        fileName: String?,
        destinationDir: String?,
        headers: Map<String, String>,
        numParts: Int
    ): DownloadItem {
        val probe = try {
            downloader.probeUrl(url, headers)
        } catch (e: Exception) {
            null
        }

        val resolvedFileName = fileName
            ?: probe?.fileName
            ?: FileUtils.extractFileNameFromUrl(probe?.finalUrl ?: url)

        val sanitized = FileUtils.sanitizeFileName(resolvedFileName)

        val settings = try {
            settingsDataStore.settingsFlow.first()
        } catch (_: Exception) {
            null
        }

        val customDirPath = destinationDir ?: settings?.customDownloadDir
        val effectiveCustomPath = customDirPath?.takeIf { !it.startsWith("content://") }
        val dir = effectiveCustomPath?.let { File(it) } ?: FileUtils.getDefaultDownloadDir(context)
        val targetFile = FileUtils.createTargetFile(context, sanitized, dir)

        val preferredParts = settings?.maxParts ?: numParts

        val totalBytes = probe?.contentLength
        val supportsRange = probe?.acceptRanges == true
        val contentType = probe?.contentType
        val mimeType = MimeTypeUtils.guessMimeType(sanitized, contentType)

        val finalParts = if (supportsRange && totalBytes != null && totalBytes > DownloadItem.SINGLE_PART_THRESHOLD) {
            if (totalBytes / preferredParts >= DownloadItem.MIN_PART_SIZE_BYTES) preferredParts else (totalBytes / DownloadItem.MIN_PART_SIZE_BYTES).toInt().coerceAtLeast(1)
        } else 1

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val item = DownloadItem(
            id = id,
            url = probe?.finalUrl ?: url,
            fileName = targetFile.name,
            mimeType = mimeType,
            totalBytes = totalBytes,
            downloadedBytes = 0,
            state = DownloadState.QUEUED,
            filePath = targetFile.absolutePath,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            supportsRange = supportsRange,
            numParts = finalParts,
            errorMessage = null,
            eTag = probe?.eTag,
            headers = headers,
            checksum = null
        )

        val entity = item.toEntity()

        val parts = if (finalParts > 1 && totalBytes != null) {
            val partSize = totalBytes / finalParts
            (0 until finalParts).map { index ->
                val start = index * partSize
                val end = if (index == finalParts - 1) totalBytes - 1 else start + partSize - 1
                DownloadPartEntity(
                    id = UUID.randomUUID().toString(),
                    downloadId = id,
                    partIndex = index,
                    startByte = start,
                    endByte = end,
                    downloadedBytes = 0,
                    state = DownloadStateEntity.QUEUED,
                    tempFilePath = null
                )
            }
        } else emptyList()

        dao.insertDownloadWithParts(entity, parts)
        return item
    }

    override suspend fun insertOrUpdate(item: DownloadItem) {
        dao.insertDownload(item.toEntity())
    }

    override suspend fun updateParts(parts: List<DownloadPart>) {
        parts.forEach { dao.updatePart(it.toEntity()) }
    }

    override suspend fun delete(id: String) {
        val entity = dao.getDownloadById(id)
        if (entity != null) {
            try { File(entity.filePath).delete() } catch (_: Exception) {}
        }
        dao.deleteDownload(id)
    }

    override suspend fun queueDownload(id: String) {
        val entity = dao.getDownloadById(id) ?: return
        dao.updateDownload(entity.copy(state = DownloadStateEntity.QUEUED, updatedAt = System.currentTimeMillis()))
        startForegroundService(id, DownloadForegroundService.ACTION_START)
    }

    override suspend fun pauseDownload(id: String) {
        startForegroundService(id, DownloadForegroundService.ACTION_PAUSE)
    }

    override suspend fun resumeDownload(id: String) {
        val entity = dao.getDownloadById(id) ?: return
        dao.updateDownload(entity.copy(state = DownloadStateEntity.QUEUED, updatedAt = System.currentTimeMillis()))
        startForegroundService(id, DownloadForegroundService.ACTION_RESUME)
    }

    override suspend fun cancelDownload(id: String) {
        startForegroundService(id, DownloadForegroundService.ACTION_CANCEL)
    }

    override suspend fun retryDownload(id: String) {
        val entity = dao.getDownloadById(id) ?: return
        dao.updateDownload(
            entity.copy(
                state = DownloadStateEntity.QUEUED,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        startForegroundService(id, DownloadForegroundService.ACTION_START)
    }

    override fun observeActiveDownloadIds(): Flow<Set<String>> {
        return dao.observeAllDownloads()
            .map { list ->
                list.filter { it.state == DownloadStateEntity.DOWNLOADING || it.state == DownloadStateEntity.QUEUED }
                    .map { it.id }
                    .toSet()
            }
    }

    private fun startForegroundService(downloadId: String, action: String) {
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            this.action = action
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        context.startForegroundService(intent)
    }
}
