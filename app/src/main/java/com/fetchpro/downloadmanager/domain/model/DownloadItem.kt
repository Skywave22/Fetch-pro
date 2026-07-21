package com.fetchpro.downloadmanager.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?, // null if unknown
    val downloadedBytes: Long,
    val state: DownloadState,
    val filePath: String, // absolute path
    val createdAt: Long, // epoch millis
    val updatedAt: Long,
    val completedAt: Long?,
    val supportsRange: Boolean,
    val numParts: Int,
    val errorMessage: String?,
    val eTag: String?,
    val headers: Map<String, String> = emptyMap(),
    val checksum: String? = null, // optional sha256 for integrity
    val speedBytesPerSecond: Long = 0,
    val estimatedTimeRemainingMs: Long? = null
) : Parcelable {

    val progress: Float
        get() = if (totalBytes != null && totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val isResumable: Boolean
        get() = supportsRange && (state == DownloadState.PAUSED || state == DownloadState.FAILED)

    companion object {
        const val DEFAULT_NUM_PARTS = 8
        const val MIN_PART_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB per part minimum to avoid overhead
        const val SINGLE_PART_THRESHOLD = 1 * 1024 * 1024L // <1MB single part
    }
}

@Parcelize
data class DownloadPart(
    val id: String,
    val downloadId: String,
    val index: Int,
    val startByte: Long,
    val endByte: Long, // inclusive, -1 if unknown / till end
    val downloadedBytes: Long,
    val state: DownloadState,
    val tempFilePath: String?
) : Parcelable {
    val length: Long
        get() = if (endByte >= 0) endByte - startByte + 1 else -1

    val isCompleted: Boolean
        get() = if (length > 0) downloadedBytes >= length else false
}
