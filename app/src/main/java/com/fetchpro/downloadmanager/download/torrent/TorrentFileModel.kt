package com.fetchpro.downloadmanager.download.torrent

enum class FilePriority {
    DONT_DOWNLOAD,
    LOW,
    NORMAL,
    HIGH
}

data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val name: String,
    val size: Long,
    val priority: FilePriority = FilePriority.NORMAL,
    val isSelected: Boolean = true
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()
}
