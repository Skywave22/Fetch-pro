package com.fetchpro.downloadmanager.data.repository

import com.fetchpro.downloadmanager.data.local.db.BookmarkDao
import com.fetchpro.downloadmanager.data.local.db.BrowserHistoryDao
import com.fetchpro.downloadmanager.domain.model.Bookmark
import com.fetchpro.downloadmanager.domain.model.BrowserHistoryEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserRepository @Inject constructor(
    private val historyDao: BrowserHistoryDao,
    private val bookmarkDao: BookmarkDao
) {

    fun observeHistory(): Flow<List<BrowserHistoryEntry>> = historyDao.observeHistory()
    fun observeBookmarks(): Flow<List<Bookmark>> = bookmarkDao.observeBookmarks()

    suspend fun addHistory(url: String, title: String?) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        historyDao.insert(BrowserHistoryEntry(url = url, title = title))
    }

    suspend fun clearHistory() = historyDao.clear()

    suspend fun toggleBookmark(url: String, title: String?): Boolean {
        val existing = bookmarkDao.getByUrl(url)
        return if (existing != null) {
            bookmarkDao.delete(existing)
            false
        } else {
            bookmarkDao.insert(Bookmark(url = url, title = title))
            true
        }
    }

    suspend fun isBookmarked(url: String): Boolean {
        return bookmarkDao.getByUrl(url) != null
    }
}
