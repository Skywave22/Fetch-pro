package com.fetchpro.downloadmanager.data.local.db

import androidx.room.*
import com.fetchpro.downloadmanager.domain.model.Bookmark
import com.fetchpro.downloadmanager.domain.model.BrowserHistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY timestamp DESC LIMIT 200")
    fun observeHistory(): Flow<List<BrowserHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BrowserHistoryEntry)

    @Query("DELETE FROM browser_history")
    suspend fun clear()

    @Query("DELETE FROM browser_history WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun observeBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): Bookmark?
}
