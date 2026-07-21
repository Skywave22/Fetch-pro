package com.fetchpro.downloadmanager.browser

import android.content.Context
import android.content.Intent
import com.fetchpro.downloadmanager.MainActivity

object DownloadInterceptor {

    /**
     * Called from browser extension or when system intercepts download.
     * For Android, we support:
     * - ACTION_VIEW intents with http/https mime that looks like attachment
     * - ACTION_SEND sharing urls
     * This helper prepares intent to open FetchPro directly with URL.
     */
    fun createFetchProIntent(context: Context, url: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    fun isDownloadableUrl(url: String, mimeType: String?, contentDisposition: String?): Boolean {
        if (contentDisposition != null && contentDisposition.contains("attachment", ignoreCase = true)) return true
        // Heuristic: if url contains common downloadable extensions
        val downloadableExts = listOf(
            ".zip", ".rar", ".7z", ".tar", ".gz", ".pdf", ".apk", ".mp4", ".mkv",
            ".mp3", ".flac", ".exe", ".iso", ".dmg", ".doc", ".docx", ".xls", ".xlsx",
            ".ppt", ".pptx", ".jpg", ".png", ".gif", ".webm"
        )
        val lower = url.lowercase()
        if (downloadableExts.any { lower.contains(it) }) return true

        // mime type not html
        if (mimeType != null) {
            if (mimeType.contains("text/html")) return false
            if (!mimeType.startsWith("text/")) return true
        }
        return false
    }
}
