package com.fetchpro.downloadmanager.data.local.db

import androidx.room.*

enum class DownloadStateEntity {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val state: DownloadStateEntity,
    val filePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val supportsRange: Boolean,
    val numParts: Int,
    val errorMessage: String?,
    val eTag: String?,
    val headersJson: String?, // serialized JSON map
    val checksum: String?
)

@Entity(
    tableName = "download_parts",
    foreignKeys = [ForeignKey(
        entity = DownloadEntity::class,
        parentColumns = ["id"],
        childColumns = ["downloadId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("downloadId")]
)
data class DownloadPartEntity(
    @PrimaryKey val id: String,
    val downloadId: String,
    val partIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val state: DownloadStateEntity,
    val tempFilePath: String?
)
