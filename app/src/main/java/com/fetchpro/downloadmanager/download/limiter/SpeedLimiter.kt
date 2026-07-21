package com.fetchpro.downloadmanager.download.limiter

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Token bucket speed limiter - 1DM+ feature: Global + individual speed limit
 * Limits download speed to specified bytes per second.
 * Thread-safe, coroutine-friendly.
 */
class SpeedLimiter(
    var bytesPerSecond: Long // 0 = unlimited
) {
    private val availableTokens = AtomicLong(0)
    private var lastRefillNanos = System.nanoTime()
    private val lock = Any()

    companion object {
        fun unlimited() = SpeedLimiter(0)
        fun fromKbps(kbps: Long) = SpeedLimiter(kbps * 1024)
        fun fromMbps(mbps: Double) = SpeedLimiter((mbps * 1024 * 1024).toLong())
    }

    fun setLimit(bps: Long) {
        bytesPerSecond = bps.coerceAtLeast(0)
        // Reset tokens when limit changes
        availableTokens.set(0)
        lastRefillNanos = System.nanoTime()
    }

    /**
     * Acquire permission to consume `bytes` bytes, suspends if needed to respect limit
     * Returns actual delay in ms (0 if no limit)
     */
    suspend fun acquire(bytes: Int): Long {
        if (bytesPerSecond <= 0) return 0 // unlimited

        var delayMs = 0L
        synchronized(lock) {
            refill()

            val available = availableTokens.get()
            if (available >= bytes) {
                availableTokens.addAndGet(-bytes.toLong())
            } else {
                // Need to wait for tokens
                val needed = bytes - available
                val waitNanos = (needed.toDouble() / bytesPerSecond.toDouble() * 1_000_000_000L).toLong()
                delayMs = (waitNanos / 1_000_000L).coerceAtLeast(1L)
                availableTokens.set(0)
                lastRefillNanos = System.nanoTime() + waitNanos
            }
        }

        if (delayMs > 0) {
            delay(delayMs)
        }
        return delayMs
    }

    private fun refill() {
        if (bytesPerSecond <= 0) {
            availableTokens.set(Long.MAX_VALUE)
            return
        }
        val now = System.nanoTime()
        val elapsedNanos = now - lastRefillNanos
        if (elapsedNanos <= 0) return

        val tokensToAdd = (elapsedNanos.toDouble() / 1_000_000_000.0 * bytesPerSecond).toLong()
        if (tokensToAdd > 0) {
            val current = availableTokens.get()
            val newTokens = min(current + tokensToAdd, bytesPerSecond * 2) // burst up to 2 seconds
            availableTokens.set(newTokens)
            lastRefillNanos = now
        }
    }
}

/**
 * Throttling InputStream wrapper for OkHttp body
 */
class ThrottlingInputStream(
    private val wrapped: java.io.InputStream,
    private val limiter: SpeedLimiter
) : java.io.InputStream() {

    override fun read(): Int {
        val b = wrapped.read()
        if (b != -1) {
            // For single byte reads, we still throttle but in batches for efficiency
            // This is slow path, but we handle via small delay
            kotlinx.coroutines.runBlocking {
                limiter.acquire(1)
            }
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = wrapped.read(b, off, len)
        if (read > 0) {
            kotlinx.coroutines.runBlocking {
                limiter.acquire(read)
            }
        }
        return read
    }

    override fun close() = wrapped.close()
    override fun available(): Int = wrapped.available()
}

/**
 * Global speed limiter manager - holds global + per-download limiters
 */
@Singleton
class SpeedLimiterManager @Inject constructor() {

    private val globalLimiter = SpeedLimiter(0) // 0 = unlimited
    private val perDownloadLimiters = ConcurrentHashMap<String, SpeedLimiter>()

    fun setGlobalLimit(bps: Long) {
        globalLimiter.setLimit(bps)
    }

    fun setPerDownloadLimit(downloadId: String, bps: Long) {
        if (bps <= 0) {
            perDownloadLimiters.remove(downloadId)
        } else {
            perDownloadLimiters[downloadId] = SpeedLimiter(bps)
        }
    }

    fun removeLimiter(downloadId: String) {
        perDownloadLimiters.remove(downloadId)
    }

    suspend fun acquire(downloadId: String, bytes: Int) {
        // Acquire from both limiters - effective limit is min of both
        // First global, then per-download
        if (globalLimiter.bytesPerSecond > 0) {
            globalLimiter.acquire(bytes)
        }
        perDownloadLimiters[downloadId]?.let { limiter ->
            if (limiter.bytesPerSecond > 0) {
                limiter.acquire(bytes)
            }
        }
    }

    fun getGlobalLimit(): Long = globalLimiter.bytesPerSecond
    fun getPerDownloadLimit(downloadId: String): Long = perDownloadLimiters[downloadId]?.bytesPerSecond ?: 0
}
