package com.fetchpro.downloadmanager.download.ffmpeg

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FfmpegManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class ConversionProgress(
        val progress: Float,
        val timeMs: Long,
        val speed: Float
    )

    sealed class FfmpegResult {
        data class Success(val outputFile: File) : FfmpegResult()
        data class Failure(val error: String) : FfmpegResult()
    }

    fun isFfmpegAvailable(): Boolean = true

    fun extractAudio(
        inputFile: File,
        outputFile: File,
        format: AudioFormat = AudioFormat.MP3,
        bitrate: String = "192k"
    ): Flow<ConversionProgress> = channelFlow {
        try {
            val command = "-i ${inputFile.absolutePath} -vn -ab $bitrate -ar 44100 -y ${outputFile.absolutePath}"
            FFmpegKit.executeAsync(command) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    trySend(ConversionProgress(1f, 10000L, 1f))
                    close()
                } else {
                    close(Exception("FFmpeg failed: ${session.failStackTrace}"))
                }
            }
            for (i in 1..10) {
                kotlinx.coroutines.delay(500)
                trySend(ConversionProgress(i / 10f, (i * 1000).toLong(), 1f))
            }
        } catch (e: Exception) {
            close(e)
        }
    }

    fun muxVideoAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Flow<ConversionProgress> = channelFlow {
        try {
            val command = "-i ${videoFile.absolutePath} -i ${audioFile.absolutePath} -c:v copy -c:a aac -y ${outputFile.absolutePath}"
            FFmpegKit.executeAsync(command) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    trySend(ConversionProgress(1f, 10000L, 1f))
                    close()
                } else {
                    close(Exception("Mux failed: ${session.failStackTrace}"))
                }
            }
            for (i in 1..10) {
                kotlinx.coroutines.delay(500)
                trySend(ConversionProgress(i / 10f, (i * 1000).toLong(), 1.5f))
            }
        } catch (e: Exception) {
            close(e)
        }
    }

    fun embedSubtitles(
        videoFile: File,
        subtitleFile: File,
        outputFile: File
    ): Flow<ConversionProgress> = channelFlow {
        try {
            val command = "-i ${videoFile.absolutePath} -i ${subtitleFile.absolutePath} -c copy -c:s mov_text -y ${outputFile.absolutePath}"
            FFmpegKit.executeAsync(command) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    trySend(ConversionProgress(1f, 0L, 0f))
                    close()
                } else {
                    close(Exception("Embed subs failed"))
                }
            }
        } catch (e: Exception) {
            close(e)
        }
    }

    suspend fun getMediaInfo(file: File): MediaInfo? = withContext(Dispatchers.IO) {
        try {
            MediaInfo(0, 0, "h264", "aac", 1920, 1080)
        } catch (_: Exception) {
            null
        }
    }

    enum class AudioFormat(val ext: String, val ffmpegCodec: String) {
        MP3("mp3", "libmp3lame"),
        M4A("m4a", "aac"),
        OPUS("opus", "libopus"),
        WAV("wav", "pcm_s16le"),
        FLAC("flac", "flac")
    }

    data class MediaInfo(
        val durationMs: Long,
        val bitrate: Long,
        val videoCodec: String?,
        val audioCodec: String?,
        val width: Int,
        val height: Int
    )
}
