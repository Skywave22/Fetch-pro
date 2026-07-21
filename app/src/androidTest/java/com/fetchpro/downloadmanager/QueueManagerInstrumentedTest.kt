package com.fetchpro.downloadmanager

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fetchpro.downloadmanager.data.local.db.DownloadDatabase
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.data.local.db.DownloadEntity
import com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
import com.fetchpro.downloadmanager.download.queue.DownloadQueueManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueManagerInstrumentedTest {

    private lateinit var db: DownloadDatabase
    private lateinit var queueManager: DownloadQueueManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = SettingsDataStore(context)
        queueManager = DownloadQueueManager(db.downloadDao(), settings)
    }

    @Test
    fun canStartNewDownloadRespectsLimit() = runTest {
        queueManager.setMaxConcurrent(2)
        // Insert 2 active downloads
        val dao = db.downloadDao()
        dao.insertDownload(
            DownloadEntity(
                id = "1",
                url = "https://a.com",
                fileName = "a",
                mimeType = null,
                totalBytes = null,
                downloadedBytes = 0,
                state = DownloadStateEntity.DOWNLOADING,
                filePath = "/tmp/a",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                completedAt = null,
                supportsRange = true,
                numParts = 1,
                errorMessage = null,
                eTag = null,
                headersJson = null,
                checksum = null
            )
        )
        dao.insertDownload(
            DownloadEntity(
                id = "2",
                url = "https://b.com",
                fileName = "b",
                mimeType = null,
                totalBytes = null,
                downloadedBytes = 0,
                state = DownloadStateEntity.DOWNLOADING,
                filePath = "/tmp/b",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                completedAt = null,
                supportsRange = true,
                numParts = 1,
                errorMessage = null,
                eTag = null,
                headersJson = null,
                checksum = null
            )
        )

        val canStart = queueManager.canStartNewDownload()
        assertFalse(canStart)

        queueManager.setMaxConcurrent(3)
        assertTrue(queueManager.canStartNewDownload())
    }
}
