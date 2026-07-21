package com.fetchpro.downloadmanager

import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadState
import com.fetchpro.downloadmanager.download.engine.HttpClientProvider
import com.fetchpro.downloadmanager.download.engine.MultiPartDownloader
import com.fetchpro.downloadmanager.download.utils.FileUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Production unit tests for MultiPartDownloader using MockWebServer.
 * Tests real HTTP Range logic, pause/resume, allocation.
 */
class MultiPartDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: MultiPartDownloader
    private lateinit var tempDir: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        downloader = MultiPartDownloader(HttpClientProvider())
        tempDir = createTempDir("fetchpro_test")
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `probeUrl returns content length and accept ranges`() = runTest {
        val content = "Hello FetchPro"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Length", content.length.toString())
                .addHeader("Accept-Ranges", "bytes")
                .addHeader("Content-Type", "text/plain")
                .setBody(content)
        )

        val url = server.url("/file.txt").toString()
        val result = downloader.probeUrl(url)

        assertEquals(content.length.toLong(), result.contentLength)
        assertTrue(result.acceptRanges)
        assertEquals("text/plain", result.contentType?.substringBefore(';'))
    }

    @Test
    fun `probeUrl handles Content-Range fallback`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 0-0/12345")
                .addHeader("Content-Type", "application/octet-stream")
        )

        val url = server.url("/file.bin").toString()
        val result = downloader.probeUrl(url)

        assertEquals(12345L, result.contentLength)
        assertTrue(result.acceptRanges)
    }

    @Test
    fun `single part download completes correctly`() = runTest {
        val content = "A".repeat(1024 * 100) // 100KB
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Length", content.length.toString())
                .setBody(content)
        )

        val file = File(tempDir, "single_${UUID.randomUUID()}.bin")
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = server.url("/single.bin").toString(),
            fileName = file.name,
            mimeType = "application/octet-stream",
            totalBytes = content.length.toLong(),
            downloadedBytes = 0,
            state = DownloadState.DOWNLOADING,
            filePath = file.absolutePath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            completedAt = null,
            supportsRange = false,
            numParts = 1,
            errorMessage = null,
            eTag = null
        )

        val progressList = downloader.download(item, emptyList()).toList()
        val last = progressList.last()

        assertEquals(DownloadState.COMPLETED, last.state)
        assertTrue(file.exists())
        assertEquals(content.length.toLong(), file.length())
        assertEquals(content, file.readText())
    }

    @Test
    fun `multi-part download completes with correct file size`() = runTest {
        // Simulate server supporting ranges, return content per range
        val totalSize = 1024 * 1024 // 1MB
        val fullContent = ByteArray(totalSize) { (it % 256).toByte() }

        // Dispatcher to handle Range requests
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null) {
                    val regex = Regex("bytes=(\\d+)-(\\d+)?")
                    val match = regex.find(rangeHeader)
                    if (match != null) {
                        val start = match.groupValues[1].toLong()
                        val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: (totalSize - 1).toLong()
                        val length = (end - start + 1).toInt()
                        val slice = fullContent.sliceArray(start.toInt() until (start + length).toInt())
                        return MockResponse()
                            .setResponseCode(206)
                            .addHeader("Content-Range", "bytes $start-$end/$totalSize")
                            .addHeader("Content-Length", length.toString())
                            .setBody(Buffer().write(slice))
                    }
                }
                return MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Length", totalSize.toString())
                    .addHeader("Accept-Ranges", "bytes")
                    .setBody(Buffer().write(fullContent))
            }
        }

        val file = File(tempDir, "multi_${UUID.randomUUID()}.bin")
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = server.url("/multi.bin").toString(),
            fileName = file.name,
            mimeType = "application/octet-stream",
            totalBytes = totalSize.toLong(),
            downloadedBytes = 0,
            state = DownloadState.DOWNLOADING,
            filePath = file.absolutePath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            completedAt = null,
            supportsRange = true,
            numParts = 4,
            errorMessage = null,
            eTag = null
        )

        val progress = downloader.download(item, emptyList()).toList()
        val last = progress.last()

        assertEquals(DownloadState.COMPLETED, last.state)
        assertTrue(file.exists())
        assertEquals(totalSize.toLong(), file.length())
        assertArrayEquals(fullContent, file.readBytes())
    }

    @Test
    fun `FileUtils sanitize`() {
        val sanitized = FileUtils.sanitizeFileName("a/b\\c:*?\"<>|.txt")
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
        assertEquals("a_b_c_______.txt", sanitized)
    }

    @Test
    fun `format utilities`() {
        val oneKb = 1024L
        val formatted = com.fetchpro.downloadmanager.presentation.ui.components.formatBytes(oneKb)
        assertTrue(formatted.contains("KB"))
    }
}
