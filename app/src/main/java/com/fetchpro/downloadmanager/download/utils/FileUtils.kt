package com.fetchpro.downloadmanager.download.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.UUID

object FileUtils {

    fun getDefaultDownloadDir(context: Context): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(publicDir, "FetchPro")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, "downloads_tmp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createTargetFile(context: Context, fileName: String, destDir: File? = null): File {
        val dir = destDir ?: getDefaultDownloadDir(context)
        if (!dir.exists()) dir.mkdirs()
        var file = File(dir, fileName)
        var counter = 1
        while (file.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast('.', fileName)
            val ext = fileName.substringAfterLast('.', "")
            val newName = if (ext.isNotEmpty() && ext != fileName) {
                "${nameWithoutExt} (${counter}).${ext}"
            } else {
                "${fileName} (${counter})"
            }
            file = File(dir, newName)
            counter++
        }
        return file
    }

    fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    fun extractFileNameFromUrl(url: String): String {
        return try {
            val path = url.substringBefore('?').substringAfterLast('/')
            if (path.isNotBlank()) sanitizeFileName(path) else "download_${UUID.randomUUID().toString().take(8)}"
        } catch (_: Exception) {
            "download_${UUID.randomUUID().toString().take(8)}"
        }
    }

    fun getFileNameFromContentDisposition(header: String?): String? {
        if (header == null) return null
        val regex = Regex("filename\\*?=\"?([^\";]+)\"?")
        val match = regex.find(header) ?: return null
        return sanitizeFileName(match.groupValues[1].trim())
    }

    fun allocateFile(file: File, size: Long) {
        if (size <= 0) return
        file.parentFile?.mkdirs()
        if (!file.exists()) file.createNewFile()
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(size)
        }
    }

    fun deleteFileSafe(file: File?) {
        try {
            file?.delete()
        } catch (_: Exception) {}
    }
}
