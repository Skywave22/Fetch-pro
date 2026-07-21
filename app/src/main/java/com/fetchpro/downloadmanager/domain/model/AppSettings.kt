package com.fetchpro.downloadmanager.domain.model

data class AppSettings(
    val maxConcurrent: Int = 3,
    val maxParts: Int = 8,
    val wifiOnly: Boolean = false,
    val customDownloadDir: String? = null,
    val autoRetry: Boolean = true,
    val keepCompletedHistoryDays: Int = 30,
    val askBeforeDownload: Boolean = false,
    // Phase 2: Speed limiter (1DM+)
    val globalSpeedLimitBps: Long = 0, // 0 = unlimited
    val retryDelayMs: Long = 5000,
    val unlimitedRetry: Boolean = true,
    // Phase 1 additions
    val smartClipboardEnabled: Boolean = false,
    val vibrationOnComplete: Boolean = true,
    val soundOnComplete: Boolean = true
)
