package com.fetchpro.downloadmanager.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fetchpro.downloadmanager.domain.model.Bookmark
import com.fetchpro.downloadmanager.domain.model.BrowserHistoryEntry

@Database(
    entities = [DownloadEntity::class, DownloadPartEntity::class, BrowserHistoryEntry::class, Bookmark::class],
    version = 2,
    exportSchema = true
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
}
