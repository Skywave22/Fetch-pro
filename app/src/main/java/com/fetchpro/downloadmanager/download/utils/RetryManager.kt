package com.fetchpro.downloadmanager.download.utils

import com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unlimited retry with custom delay - 1DM+ feature
 * Previously we had 3 retries with exponential backoff
 * Now supports unlimited retries with configurable delay
 */
@Singleton
class RetryManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {

    data class RetryConfig(
        val maxRetries: Int = -1, // -1 = unlimited
        val delayMs: Long = 5000, // custom delay
        val exponential: Boolean = true
    )

    suspend fun getConfig(): RetryConfig {
        return try {
            val settings = settingsDataStore.settingsFlow.first()
            RetryConfig(
                maxRetries = if (settings.autoRetry) -1 else 3, // unlimited if autoRetry enabled, else 3
                delayMs = 5000,
                exponential = true
            )
        } catch (_: Exception) {
            RetryConfig()
        }
    }

    suspend fun shouldRetry(attempt: Int, config: RetryConfig): Boolean {
        if (config.maxRetries == -1) return true // unlimited
        return attempt < config.maxRetries
    }

    suspend fun getDelay(attempt: Int, config: RetryConfig): Long {
        return if (config.exponential) {
            (config.delayMs * (1 shl attempt.coerceAtMost(6))).coerceAtMost(60000L) // cap at 1 min
        } else {
            config.delayMs
        }
    }
}
