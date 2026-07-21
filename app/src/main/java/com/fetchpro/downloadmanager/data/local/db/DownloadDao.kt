package com.fetchpro.downloadmanager.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeDownloadById(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state IN (:states) ORDER BY createdAt ASC")
    suspend fun getDownloadsByStates(states: List<DownloadStateEntity>): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("SELECT * FROM download_parts WHERE downloadId = :downloadId ORDER BY partIndex ASC")
    suspend fun getPartsForDownload(downloadId: String): List<DownloadPartEntity>

    @Query("SELECT * FROM download_parts WHERE downloadId = :downloadId ORDER BY partIndex ASC")
    fun observePartsForDownload(downloadId: String): Flow<List<DownloadPartEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<DownloadPartEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: DownloadPartEntity)

    @Update
    suspend fun updatePart(part: DownloadPartEntity)

    @Query("DELETE FROM download_parts WHERE downloadId = :downloadId")
    suspend fun deletePartsForDownload(downloadId: String)

    @Transaction
    suspend fun insertDownloadWithParts(download: DownloadEntity, parts: List<DownloadPartEntity>) {
        insertDownload(download)
        if (parts.isNotEmpty()) {
            insertParts(parts)
        }
    }

    @Query("SELECT * FROM downloads WHERE state = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeHistory(): Flow<List<DownloadEntity>>
}
