package com.fetchpro.downloadmanager.domain.repository

import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadPart
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadItem>>
    fun observeById(id: String): Flow<DownloadItem?>
    fun observeHistory(): Flow<List<DownloadItem>>

    suspend fun getById(id: String): DownloadItem?
    suspend fun getParts(downloadId: String): List<DownloadPart>

    suspend fun createDownload(
        url: String,
        fileName: String?,
        destinationDir: String?,
        headers: Map<String, String>,
        numParts: Int
    ): DownloadItem

    suspend fun insertOrUpdate(item: DownloadItem)
    suspend fun updateParts(parts: List<DownloadPart>)
    suspend fun delete(id: String)

    suspend fun queueDownload(id: String)
    suspend fun pauseDownload(id: String)
    suspend fun resumeDownload(id: String)
    suspend fun cancelDownload(id: String)
    suspend fun retryDownload(id: String)

    fun observeActiveDownloadIds(): Flow<Set<String>>
}
