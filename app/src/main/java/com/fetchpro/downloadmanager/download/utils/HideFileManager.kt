package com.fetchpro.downloadmanager.download.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hide downloaded files from everyone - 1DM+ feature
 * Implements:
 * - .nomedia file creation to hide from gallery
 * - Optional hidden folder (dot prefix)
 */
@Singleton
class HideFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Hide file by creating .nomedia in its directory
     */
    fun hideFromGallery(directory: File): Boolean {
        return try {
            val nomedia = File(directory, ".nomedia")
            if (!nomedia.exists()) {
                nomedia.createNewFile()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Create hidden download directory (dot prefix)
     */
    fun getHiddenDownloadDir(context: Context): File {
        val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val hiddenDir = File(publicDir, ".FetchPro")
        if (!hiddenDir.exists()) {
            hiddenDir.mkdirs()
            // Create .nomedia
            File(hiddenDir, ".nomedia").createNewFile()
        }
        return hiddenDir
    }

    /**
     * Check if file should be hidden based on settings
     */
    fun shouldHideFile(fileName: String, mimeType: String?): Boolean {
        // For now, respect user setting - could be extended to hide by category
        return false // Will be controlled by setting
    }
}
