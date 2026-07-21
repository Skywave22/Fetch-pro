package com.fetchpro.downloadmanager.data.local.db

import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadPart
import com.fetchpro.downloadmanager.domain.model.DownloadState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private val json = Json { ignoreUnknownKeys = true }

fun DownloadState.toEntity(): DownloadStateEntity = when (this) {
    DownloadState.QUEUED -> DownloadStateEntity.QUEUED
    DownloadState.DOWNLOADING -> DownloadStateEntity.DOWNLOADING
    DownloadState.PAUSED -> DownloadStateEntity.PAUSED
    DownloadState.COMPLETED -> DownloadStateEntity.COMPLETED
    DownloadState.FAILED -> DownloadStateEntity.FAILED
    DownloadState.CANCELLED -> DownloadStateEntity.CANCELLED
}

fun DownloadStateEntity.toDomain(): DownloadState = when (this) {
    DownloadStateEntity.QUEUED -> DownloadState.QUEUED
    DownloadStateEntity.DOWNLOADING -> DownloadState.DOWNLOADING
    DownloadStateEntity.PAUSED -> DownloadState.PAUSED
    DownloadStateEntity.COMPLETED -> DownloadState.COMPLETED
    DownloadStateEntity.FAILED -> DownloadState.FAILED
    DownloadStateEntity.CANCELLED -> DownloadState.CANCELLED
}

fun DownloadEntity.toDomain(): DownloadItem {
    val headersMap = try {
        headersJson?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }
    return DownloadItem(
        id = id,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        state = state.toDomain(),
        filePath = filePath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        supportsRange = supportsRange,
        numParts = numParts,
        errorMessage = errorMessage,
        eTag = eTag,
        headers = headersMap,
        checksum = checksum
    )
}

fun DownloadItem.toEntity(): DownloadEntity {
    return DownloadEntity(
        id = id,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        state = state.toEntity(),
        filePath = filePath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        supportsRange = supportsRange,
        numParts = numParts,
        errorMessage = errorMessage,
        eTag = eTag,
        headersJson = try { Json.encodeToString(headers) } catch (_: Exception) { "{}" },
        checksum = checksum
    )
}

fun DownloadPartEntity.toDomain(): DownloadPart = DownloadPart(
    id = id,
    downloadId = downloadId,
    index = partIndex,
    startByte = startByte,
    endByte = endByte,
    downloadedBytes = downloadedBytes,
    state = state.toDomain(),
    tempFilePath = tempFilePath
)

fun DownloadPart.toEntity(): DownloadPartEntity = DownloadPartEntity(
    id = id,
    downloadId = downloadId,
    partIndex = index,
    startByte = startByte,
    endByte = endByte,
    downloadedBytes = downloadedBytes,
    state = state.toEntity(),
    tempFilePath = tempFilePath
)
