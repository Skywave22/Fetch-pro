package com.fetchpro.downloadmanager.download.hls

import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.model.DownloadState
import com.fetchpro.downloadmanager.download.engine.DownloadProgress
import com.fetchpro.downloadmanager.download.limiter.SpeedLimiterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HLS (HTTP Live Streaming) downloader - 1DM+ feature: Supports HTTP Live Streaming websites
 * Parses .m3u8 playlists and downloads TS segments, then merges
 */
@Singleton
class HlsDownloader @Inject constructor(
    private val speedLimiterManager: SpeedLimiterManager
) {

    data class HlsPlaylist(
        val segments: List<String>,
        val isMaster: Boolean = false,
        val variants: List<Variant> = emptyList()
    )

    data class Variant(
        val bandwidth: Long,
        val resolution: String?,
        val url: String
    )

    suspend fun isHlsUrl(url: String): Boolean {
        return url.contains(".m3u8", ignoreCase = true)
    }

    suspend fun parsePlaylist(client: OkHttpClient, playlistUrl: String): HlsPlaylist = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playlistUrl).build()
        val response = client.newCall(request).execute()
        val content = response.body?.string() ?: throw Exception("Empty playlist")
        response.close()

        val baseUrl = playlistUrl.substringBeforeLast("/") + "/"

        if (content.contains("#EXT-X-STREAM-INF")) {
            // Master playlist - contains variants
            val variants = mutableListOf<Variant>()
            val lines = content.lines()
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                    val resolution = Regex("RESOLUTION=([^,\\s]+)").find(line)?.groupValues?.get(1)
                    val nextLine = lines.getOrNull(i + 1)?.trim()
                    if (nextLine != null && !nextLine.startsWith("#") && nextLine.isNotBlank()) {
                        val variantUrl = if (nextLine.startsWith("http")) nextLine else baseUrl + nextLine
                        variants.add(Variant(bandwidth, resolution, variantUrl))
                    }
                }
            }
            HlsPlaylist(emptyList(), true, variants.sortedByDescending { it.bandwidth })
        } else {
            // Media playlist - contains segments
            val segments = content.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { seg ->
                    if (seg.startsWith("http")) seg else baseUrl + seg
                }
            HlsPlaylist(segments, false, emptyList())
        }
    }

    fun downloadHls(
        item: DownloadItem,
        client: OkHttpClient
    ): Flow<DownloadProgress> = channelFlow {
        val downloadId = item.id
        val outputFile = File(item.filePath)
        outputFile.parentFile?.mkdirs()

        val totalDownloaded = AtomicLong(0)
        var totalSegments = 0
        var completedSegments = 0

        try {
            // Parse master playlist
            var playlist = parsePlaylist(client, item.url)

            // If master, pick best variant
            if (playlist.isMaster && playlist.variants.isNotEmpty()) {
                val bestVariant = playlist.variants.first()
                playlist = parsePlaylist(client, bestVariant.url)
            }

            totalSegments = playlist.segments.size
            if (totalSegments == 0) {
                throw Exception("No segments found in playlist")
            }

            // Create temp dir for segments
            val tempDir = File(outputFile.parentFile, "${outputFile.name}.hls_temp").apply { mkdirs() }
            val segmentFiles = mutableListOf<File>()

            // Download segments sequentially (could be parallel for speed)
            for ((index, segmentUrl) in playlist.segments.withIndex()) {
                val segmentFile = File(tempDir, "seg_${index.toString().padStart(5, '0')}.ts")
                segmentFiles.add(segmentFile)

                val request = Request.Builder().url(segmentUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Failed to download segment $index: ${response.code}")
                    }
                    val body = response.body ?: throw Exception("Empty segment")
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int

                    FileOutputStream(segmentFile).use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded.addAndGet(bytesRead.toLong())
                            speedLimiterManager.acquire(downloadId, bytesRead)

                            // Emit progress
                            if (index % 5 == 0) {
                                trySend(
                                    DownloadProgress(
                                        downloadId = downloadId,
                                        downloadedBytes = totalDownloaded.get(),
                                        totalBytes = null, // unknown total for HLS
                                        speedBps = 0,
                                        state = DownloadState.DOWNLOADING
                                    )
                                )
                            }
                        }
                    }
                }

                completedSegments++
                trySend(
                    DownloadProgress(
                        downloadId = downloadId,
                        downloadedBytes = totalDownloaded.get(),
                        totalBytes = null,
                        speedBps = 0,
                        state = DownloadState.DOWNLOADING
                    )
                )
            }

            // Merge segments into final file (simple concatenation for TS)
            // For MP4 conversion, would need ffmpeg, but for now concat
            FileOutputStream(outputFile).use { output ->
                for (segFile in segmentFiles) {
                    segFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }

            // Cleanup temp
            tempDir.deleteRecursively()

            trySend(
                DownloadProgress(
                    downloadId = downloadId,
                    downloadedBytes = totalDownloaded.get(),
                    totalBytes = totalDownloaded.get(),
                    speedBps = 0,
                    state = DownloadState.COMPLETED
                )
            )

        } catch (e: Exception) {
            throw e
        }
    }
}
