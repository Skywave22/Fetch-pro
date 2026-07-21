package com.fetchpro.downloadmanager.domain.usecase

import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.domain.repository.DownloadRepository
import com.fetchpro.downloadmanager.download.engine.MultiPartDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Refresh expired links - 1DM+ feature
 * Re-probes the URL to get new final URL, content-length, etc.
 * Useful when download link has expired and server provides new URL via redirect
 */
class RefreshExpiredLinkUseCase @Inject constructor(
    private val dao: DownloadDao,
    private val downloader: MultiPartDownloader,
    private val repository: DownloadRepository
) {

    data class RefreshResult(
        val success: Boolean,
        val newUrl: String? = null,
        val newFileName: String? = null,
        val newSize: Long? = null,
        val message: String? = null
    )

    suspend operator fun invoke(downloadId: String): RefreshResult = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getDownloadById(downloadId) ?: return@withContext RefreshResult(false, message = "Download not found")

            // Probe the original URL again
            val probe = downloader.probeUrl(entity.url)

            // Update entity with new info if changed
            val updated = entity.copy(
                url = probe.finalUrl,
                totalBytes = probe.contentLength ?: entity.totalBytes,
                fileName = probe.fileName ?: entity.fileName,
                updatedAt = System.currentTimeMillis(),
                errorMessage = null
            )

            dao.updateDownload(updated)

            RefreshResult(
                success = true,
                newUrl = probe.finalUrl,
                newFileName = probe.fileName,
                newSize = probe.contentLength,
                message = "Link refreshed successfully"
            )
        } catch (e: Exception) {
            RefreshResult(false, message = e.message ?: "Failed to refresh")
        }
    }
}
