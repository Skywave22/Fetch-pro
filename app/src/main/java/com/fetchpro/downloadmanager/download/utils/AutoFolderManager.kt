package com.fetchpro.downloadmanager.download.utils

import android.content.Context
import com.fetchpro.downloadmanager.domain.model.DownloadCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto folder by file type - ADM + FDM feature
 * Save different file types in different folders
 * VIDEO -> Movies, AUDIO -> Music, etc.
 */
@Singleton
class AutoFolderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class FolderMapping(
        val category: DownloadCategory,
        val folderName: String,
        val enabled: Boolean = true
    )

    private val defaultMappings = listOf(
        FolderMapping(DownloadCategory.VIDEO, "Movies", true),
        FolderMapping(DownloadCategory.AUDIO, "Music", true),
        FolderMapping(DownloadCategory.IMAGE, "Pictures", true),
        FolderMapping(DownloadCategory.DOCUMENT, "Documents", true),
        FolderMapping(DownloadCategory.ARCHIVE, "Archives", true),
        FolderMapping(DownloadCategory.APK, "APKs", true),
        FolderMapping(DownloadCategory.OTHER, "Others", false)
    )

    private var currentMappings: List<FolderMapping> = defaultMappings

    fun getFolderForCategory(category: DownloadCategory, baseDir: File): File {
        val mapping = currentMappings.find { it.category == category && it.enabled }
            ?: currentMappings.find { it.category == DownloadCategory.OTHER }
        val folderName = mapping?.folderName ?: "Others"

        return if (mapping?.enabled == true) {
            File(baseDir, folderName).apply { if (!exists()) mkdirs() }
        } else {
            baseDir
        }
    }

    fun getFolderForFileName(fileName: String, mimeType: String?, baseDir: File): File {
        val category = DownloadCategory.fromMimeType(mimeType, fileName)
        return getFolderForCategory(category, baseDir)
    }

    fun updateMapping(category: DownloadCategory, folderName: String, enabled: Boolean) {
        currentMappings = currentMappings.map { mapping ->
            if (mapping.category == category) {
                mapping.copy(folderName = folderName, enabled = enabled)
            } else mapping
        }
    }

    fun getMappings(): List<FolderMapping> = currentMappings

    fun resetToDefaults() {
        currentMappings = defaultMappings
    }

    /**
     * Organize existing downloads by type (retroactive)
     */
    fun organizeExistingFiles(baseDir: File): Map<File, File> {
        val moves = mutableMapOf<File, File>()
        if (!baseDir.exists()) return moves

        baseDir.listFiles()?.forEach { file ->
            if (file.isFile && !file.name.startsWith(".")) {
                val category = DownloadCategory.fromMimeType(null, file.name)
                val targetDir = getFolderForCategory(category, baseDir)
                if (targetDir != baseDir && targetDir.exists()) {
                    val targetFile = File(targetDir, file.name)
                    if (!targetFile.exists()) {
                        moves[file] = targetFile
                    }
                }
            }
        }
        return moves
    }
}
