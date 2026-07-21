package com.fetchpro.downloadmanager.domain.model

enum class DownloadCategory {
    ALL,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    APK,
    IMAGE,
    OTHER;

    companion object {
        fun fromMimeType(mimeType: String?, fileName: String?): DownloadCategory {
            val mime = mimeType?.lowercase() ?: ""
            val name = fileName?.lowercase() ?: ""
            return when {
                mime.startsWith("video/") || listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv").any { name.endsWith(it) } -> VIDEO
                mime.startsWith("audio/") || listOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg").any { name.endsWith(it) } -> AUDIO
                mime.startsWith("image/") || listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").any { name.endsWith(it) } -> IMAGE
                mime.contains("pdf") || mime.contains("msword") || mime.contains("document") || listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt").any { name.endsWith(it) } -> DOCUMENT
                mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") || listOf(".zip", ".rar", ".7z", ".tar", ".gz").any { name.endsWith(it) } -> ARCHIVE
                mime.contains("apk") || name.endsWith(".apk") -> APK
                else -> OTHER
            }
        }
    }
}

enum class DownloadSortBy {
    NAME,
    SIZE,
    DATE_NEWEST,
    DATE_OLDEST;

    fun sort(list: List<DownloadItem>): List<DownloadItem> {
        return when (this) {
            NAME -> list.sortedBy { it.fileName.lowercase() }
            SIZE -> list.sortedByDescending { it.totalBytes ?: it.downloadedBytes }
            DATE_NEWEST -> list.sortedByDescending { it.createdAt }
            DATE_OLDEST -> list.sortedBy { it.createdAt }
        }
    }
}

enum class DownloadTimeFilter {
    ALL,
    TODAY,
    THIS_WEEK,
    THIS_MONTH;

    fun filter(list: List<DownloadItem>): List<DownloadItem> {
        if (this == ALL) return list
        val now = System.currentTimeMillis()
        val cutoff = when (this) {
            TODAY -> now - 24 * 60 * 60 * 1000L
            THIS_WEEK -> now - 7 * 24 * 60 * 60 * 1000L
            THIS_MONTH -> now - 30L * 24 * 60 * 60 * 1000L
            else -> 0L
        }
        return list.filter { it.createdAt >= cutoff }
    }
}
