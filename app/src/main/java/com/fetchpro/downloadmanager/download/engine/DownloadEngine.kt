package com.fetchpro.downloadmanager.download.engine

import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadPart
import com.fetchpro.downloadmanager.domain.model.DownloadState
import kotlinx.coroutines.flow.Flow

data class DownloadProgress(
    val downloadId: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBps: Long,
    val state: DownloadState,
    val parts: List<DownloadPart> = emptyList()
)

interface DownloadEngine {
    suspend fun probeUrl(url: String, headers: Map<String, String> = emptyMap()): ProbeResult
    fun download(item: DownloadItem, parts: List<DownloadPart> = emptyList()): Flow<DownloadProgress>
    suspend fun pause(downloadId: String)
    suspend fun cancel(downloadId: String)
}

data class ProbeResult(
    val url: String,
    val finalUrl: String,
    val contentLength: Long?,
    val acceptRanges: Boolean,
    val eTag: String?,
    val contentType: String?,
    val fileName: String?,
    val headers: Map<String, String>
)
