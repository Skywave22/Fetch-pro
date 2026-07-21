package com.fetchpro.downloadmanager.download.engine

import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadPart
import com.fetchpro.downloadmanager.domain.model.DownloadState
import com.fetchpro.downloadmanager.download.limiter.SpeedLimiterManager
import com.fetchpro.downloadmanager.download.utils.FileUtils
import com.fetchpro.downloadmanager.download.utils.RetryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiPartDownloader @Inject constructor(
    private val httpClientProvider: HttpClientProvider,
    private val speedLimiterManager: SpeedLimiterManager,
    private val retryManager: RetryManager
) : DownloadEngine {

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()

    override suspend fun probeUrl(url: String, headers: Map<String, String>): ProbeResult {
        return withContext(Dispatchers.IO) {
            val client = httpClientProvider.client
            var requestBuilder = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            requestBuilder.addHeader("User-Agent", "FetchPro/1.0")

            var response = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (_: Exception) {
                null
            }

            if (response == null || !response.isSuccessful) {
                response?.close()
                requestBuilder = Request.Builder().url(url).get()
                    .addHeader("Range", "bytes=0-0")
                headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                requestBuilder.addHeader("User-Agent", "FetchPro/1.0")
                response = client.newCall(requestBuilder.build()).execute()
            }

            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    throw IOException("Probe failed: ${resp.code} ${resp.message}")
                }

                val contentLength = resp.header("Content-Range")?.let { cr ->
                    cr.substringAfterLast('/').toLongOrNull()
                } ?: resp.header("Content-Length")?.toLongOrNull()
                    ?: resp.body?.contentLength()?.takeIf { it > 0 }

                val acceptRanges = resp.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true ||
                        resp.header("Content-Range") != null

                val eTag = resp.header("ETag")
                val contentType = resp.header("Content-Type")
                val disposition = resp.header("Content-Disposition")
                val fileName = FileUtils.getFileNameFromContentDisposition(disposition)

                val finalUrl = resp.request.url.toString()
                val allHeaders = resp.headers.toMultimap().mapValues { it.value.firstOrNull() ?: "" }

                ProbeResult(
                    url = url,
                    finalUrl = finalUrl,
                    contentLength = contentLength,
                    acceptRanges = acceptRanges,
                    eTag = eTag,
                    contentType = contentType,
                    fileName = fileName,
                    headers = allHeaders
                )
            }
        }
    }

    override fun download(item: DownloadItem, parts: List<DownloadPart>): Flow<DownloadProgress> = channelFlow {
        val downloadId = item.id
        val file = File(item.filePath)
        val totalBytes = item.totalBytes
        val supportsRange = item.supportsRange

        val paused = AtomicBoolean(false)
        pauseFlags[downloadId] = paused

        val totalDownloaded = AtomicLong(item.downloadedBytes)
        val lastEmitTime = AtomicLong(System.currentTimeMillis())
        val lastBytes = AtomicLong(item.downloadedBytes)

        val activeParts = if (supportsRange && totalBytes != null && totalBytes > DownloadItem.SINGLE_PART_THRESHOLD) {
            prepareParts(item, parts)
        } else {
            listOf(
                DownloadPart(
                    id = UUID.randomUUID().toString(),
                    downloadId = downloadId,
                    index = 0,
                    startByte = item.downloadedBytes,
                    endByte = totalBytes?.let { it - 1 } ?: -1,
                    downloadedBytes = item.downloadedBytes,
                    state = DownloadState.DOWNLOADING,
                    tempFilePath = null
                )
            )
        }

        if (totalBytes != null && totalBytes > 0 && !file.exists()) {
            try {
                FileAllocator.allocate(file, totalBytes)
            } catch (e: Exception) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
        } else {
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
        }

        val speedCalc: () -> Long = {
            val now = System.currentTimeMillis()
            val elapsed = now - lastEmitTime.get()
            if (elapsed > 0) {
                val delta = totalDownloaded.get() - lastBytes.get()
                (delta * 1000L / elapsed).coerceAtLeast(0)
            } else 0L
        }

        val job = launch(Dispatchers.IO) {
            try {
                if (activeParts.size == 1) {
                    downloadSinglePart(
                        file = file,
                        part = activeParts[0],
                        item = item,
                        totalDownloaded = totalDownloaded,
                        paused = paused,
                        onChunk = { _ ->
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime.get() > 500) {
                                trySend(
                                    DownloadProgress(
                                        downloadId = downloadId,
                                        downloadedBytes = totalDownloaded.get(),
                                        totalBytes = totalBytes,
                                        speedBps = speedCalc(),
                                        state = DownloadState.DOWNLOADING,
                                        parts = activeParts
                                    )
                                )
                                lastEmitTime.set(now)
                                lastBytes.set(totalDownloaded.get())
                            }
                        }
                    )
                } else {
                    coroutineScope {
                        val partJobs = activeParts.map { part ->
                            async {
                                downloadSinglePart(
                                    file = file,
                                    part = part,
                                    item = item,
                                    totalDownloaded = totalDownloaded,
                                    paused = paused,
                                    onChunk = { _ -> }
                                )
                            }
                        }
                        val progressJob = launch {
                            while (isActive) {
                                delay(500)
                                trySend(
                                    DownloadProgress(
                                        downloadId = downloadId,
                                        downloadedBytes = totalDownloaded.get(),
                                        totalBytes = totalBytes,
                                        speedBps = speedCalc(),
                                        state = DownloadState.DOWNLOADING,
                                        parts = activeParts
                                    )
                                )
                                lastEmitTime.set(System.currentTimeMillis())
                                lastBytes.set(totalDownloaded.get())
                            }
                        }
                        partJobs.awaitAll()
                        progressJob.cancel()
                    }
                }

                if (paused.get()) {
                    trySend(
                        DownloadProgress(
                            downloadId = downloadId,
                            downloadedBytes = totalDownloaded.get(),
                            totalBytes = totalBytes,
                            speedBps = 0,
                            state = DownloadState.PAUSED,
                            parts = activeParts
                        )
                    )
                } else {
                    if (totalBytes != null && totalDownloaded.get() < totalBytes) {
                        throw IOException("Incomplete download: ${totalDownloaded.get()}/$totalBytes")
                    }
                    trySend(
                        DownloadProgress(
                            downloadId = downloadId,
                            downloadedBytes = totalDownloaded.get(),
                            totalBytes = totalBytes,
                            speedBps = 0,
                            state = DownloadState.COMPLETED,
                            parts = activeParts
                        )
                    )
                }
            } catch (e: CancellationException) {
                if (paused.get()) {
                    trySend(
                        DownloadProgress(
                            downloadId = downloadId,
                            downloadedBytes = totalDownloaded.get(),
                            totalBytes = totalBytes,
                            speedBps = 0,
                            state = DownloadState.PAUSED,
                            parts = activeParts
                        )
                    )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                if (!paused.get()) {
                    throw e
                }
            }
        }

        activeJobs[downloadId] = job
        job.invokeOnCompletion {
            activeJobs.remove(downloadId)
            pauseFlags.remove(downloadId)
            speedLimiterManager.removeLimiter(downloadId)
        }

        awaitClose {
            job.cancel()
            speedLimiterManager.removeLimiter(downloadId)
        }
    }.flowOn(Dispatchers.IO)
        .retryWhen { cause, attempt ->
            val config = retryManager.getConfig()
            if (!retryManager.shouldRetry(attempt.toInt(), config)) return@retryWhen false
            if (cause is IOException) {
                val delayMs = retryManager.getDelay(attempt.toInt(), config)
                delay(delayMs)
                true
            } else false
        }

    private fun prepareParts(item: DownloadItem, existingParts: List<DownloadPart>): List<DownloadPart> {
        if (existingParts.isNotEmpty() && existingParts.size == item.numParts) {
            return existingParts
        }
        val total = item.totalBytes ?: return emptyList()
        val numParts = item.numParts.coerceAtLeast(1)
        val partSize = total / numParts
        return (0 until numParts).map { index ->
            val start = index * partSize
            val end = if (index == numParts - 1) total - 1 else (start + partSize - 1)
            val existing = existingParts.find { it.index == index }
            DownloadPart(
                id = existing?.id ?: UUID.randomUUID().toString(),
                downloadId = item.id,
                index = index,
                startByte = start,
                endByte = end,
                downloadedBytes = existing?.downloadedBytes ?: 0L,
                state = DownloadState.DOWNLOADING,
                tempFilePath = null
            )
        }
    }

    private suspend fun downloadSinglePart(
        file: File,
        part: DownloadPart,
        item: DownloadItem,
        totalDownloaded: AtomicLong,
        paused: AtomicBoolean,
        onChunk: (Long) -> Unit
    ) {
        if (paused.get()) return
        val client = httpClientProvider.newClientForDownload()

        val startOffset = part.startByte + part.downloadedBytes
        val end = part.endByte

        if (end >= 0 && startOffset > end) return

        val requestBuilder = Request.Builder().url(item.url)
        if (end >= 0) {
            requestBuilder.addHeader("Range", "bytes=$startOffset-$end")
        } else if (startOffset > 0) {
            requestBuilder.addHeader("Range", "bytes=$startOffset-")
        }

        item.headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        item.eTag?.let { requestBuilder.addHeader("If-Match", it) }
        requestBuilder.addHeader("User-Agent", "FetchPro/1.0")
        requestBuilder.get()

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 206 && !(response.code == 200 && startOffset == 0L)) {
                throw IOException("HTTP ${response.code} ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty body")
            val input = body.byteStream()
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(startOffset)
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (paused.get()) break
                    currentCoroutineContext().ensureActive()

                    raf.write(buffer, 0, bytesRead)
                    totalDownloaded.addAndGet(bytesRead.toLong())
                    // Speed limiter - throttle here
                    speedLimiterManager.acquire(item.id, bytesRead)
                    onChunk(bytesRead.toLong())
                }
            }
        }
    }

    override suspend fun pause(downloadId: String) {
        pauseFlags[downloadId]?.set(true)
        activeJobs[downloadId]?.cancelAndJoin()
    }

    override suspend fun cancel(downloadId: String) {
        pauseFlags[downloadId]?.set(false)
        activeJobs[downloadId]?.cancelAndJoin()
        activeJobs.remove(downloadId)
        pauseFlags.remove(downloadId)
        speedLimiterManager.removeLimiter(downloadId)
    }
}
