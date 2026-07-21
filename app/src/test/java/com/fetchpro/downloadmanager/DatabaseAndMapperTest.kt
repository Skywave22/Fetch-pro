package com.fetchpro.downloadmanager

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fetchpro.downloadmanager.data.local.db.*
import com.fetchpro.downloadmanager.domain.model.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseAndMapperTest {

    private lateinit var db: DownloadDatabase
    private lateinit var dao: DownloadDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.downloadDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and observe download`() = runTest {
        val entity = DownloadEntity(
            id = "test-id",
            url = "https://example.com/file.zip",
            fileName = "file.zip",
            mimeType = "application/zip",
            totalBytes = 1000,
            downloadedBytes = 0,
            state = DownloadStateEntity.QUEUED,
            filePath = "/tmp/file.zip",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            completedAt = null,
            supportsRange = true,
            numParts = 4,
            errorMessage = null,
            eTag = "\"etag123\"",
            headersJson = "{}",
            checksum = null
        )
        dao.insertDownload(entity)

        val observed = dao.observeAllDownloads().first()
        assertEquals(1, observed.size)
        assertEquals("file.zip", observed[0].fileName)

        val domain = observed[0].toDomain()
        assertEquals(DownloadState.QUEUED, domain.state)
        assertTrue(domain.supportsRange)
    }

    @Test
    fun `insert with parts and query`() = runTest {
        val downloadId = "dl-with-parts"
        val download = DownloadEntity(
            id = downloadId,
            url = "https://example.com/large.bin",
            fileName = "large.bin",
            mimeType = null,
            totalBytes = 10_000,
            downloadedBytes = 0,
            state = DownloadStateEntity.DOWNLOADING,
            filePath = "/tmp/large.bin",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            completedAt = null,
            supportsRange = true,
            numParts = 2,
            errorMessage = null,
            eTag = null,
            headersJson = null,
            checksum = null
        )
        val parts = listOf(
            DownloadPartEntity("p1", downloadId, 0, 0, 4999, 0, DownloadStateEntity.DOWNLOADING, null),
            DownloadPartEntity("p2", downloadId, 1, 5000, 9999, 0, DownloadStateEntity.DOWNLOADING, null)
        )
        dao.insertDownloadWithParts(download, parts)

        val retrievedParts = dao.getPartsForDownload(downloadId)
        assertEquals(2, retrievedParts.size)
        assertEquals(0L, retrievedParts[0].startByte)
        assertEquals(4999L, retrievedParts[0].endByte)
    }

    @Test
    fun `state mapping`() {
        assertEquals(DownloadState.DOWNLOADING, DownloadStateEntity.DOWNLOADING.toDomain())
        assertEquals(DownloadStateEntity.FAILED, DownloadState.FAILED.toEntity())
    }
}
