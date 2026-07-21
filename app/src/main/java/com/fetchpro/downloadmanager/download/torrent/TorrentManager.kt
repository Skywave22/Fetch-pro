package com.fetchpro.downloadmanager.download.torrent

import android.content.Context
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Torrent support - 1DM+ feature: Download Torrent files using magnet link, torrent url or torrent file
 * Uses jlibtorrent (libtorrent4j)
 */
@Singleton
class TorrentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var sessionManager: SessionManager? = null

    data class TorrentDownloadInfo(
        val id: String,
        val name: String,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val progress: Float,
        val downloadRate: Long,
        val uploadRate: Long,
        val numPeers: Int,
        val numSeeds: Int,
        val state: TorrentState,
        val savePath: String
    )

    enum class TorrentState {
        QUEUED,
        DOWNLOADING,
        SEEDING,
        PAUSED,
        FINISHED,
        FAILED
    }

    fun initializeSession(downloadDir: File) {
        if (sessionManager != null) return
        sessionManager = SessionManager().apply {
            // Start session
            start()
            // Optional: enable DHT, LSD, etc.
        }
    }

    fun downloadTorrent(
        magnetOrFile: String,
        saveDir: File
    ): Flow<TorrentDownloadInfo> = callbackFlow {
        try {
            initializeSession(saveDir)

            // In production, use jlibtorrent to parse magnet:
            // val params = com.frostwire.jlibtorrent.TorrentInfo.parseMagnetUri(magnetOrFile) // API varies by version
            // For compatibility with 1.2.0.18, avoid using parseMagnetUri which may not exist
            // Placeholder params
            val params: Any? = null

            var downloadedBytes = 0L
            var totalBytes = 0L

            val torrentId = magnetOrFile.hashCode().toString()

            trySend(
                TorrentDownloadInfo(
                    id = torrentId,
                    name = extractName(magnetOrFile),
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    progress = 0f,
                    downloadRate = 0,
                    uploadRate = 0,
                    numPeers = 0,
                    numSeeds = 0,
                    state = TorrentState.DOWNLOADING,
                    savePath = saveDir.absolutePath
                )
            )

            // TODO: Implement real torrent download loop with alerts
            // sessionManager?.addListener { alert ->
            //   when(alert) {
            //     is TorrentFinishedAlert -> close
            //     is BlockFinishedAlert -> update progress
            //   }
            // }

            awaitClose {
                // Cleanup
            }

        } catch (e: Exception) {
            close(e)
        }
    }

    fun extractName(magnetOrFile: String): String {
        return try {
            if (magnetOrFile.startsWith("magnet:")) {
                val dnMatch = Regex("dn=([^&]+)").find(magnetOrFile)
                dnMatch?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: "Torrent"
            } else {
                File(magnetOrFile).nameWithoutExtension.ifBlank { "Torrent" }
            }
        } catch (_: Exception) {
            "Torrent"
        }
    }

    fun isMagnetLink(url: String): Boolean {
        return url.startsWith("magnet:") || url.endsWith(".torrent") || url.contains("application/x-bittorrent")
    }

    fun pauseTorrent(torrentId: String) {
        // sessionManager?.find(... )?.pause()
    }

    fun resumeTorrent(torrentId: String) {
        // sessionManager?.find(... )?.resume()
    }

    fun removeTorrent(torrentId: String, deleteFiles: Boolean = false) {
        // sessionManager?.remove(...)
    }

    fun getSession(): SessionManager? = sessionManager
}
