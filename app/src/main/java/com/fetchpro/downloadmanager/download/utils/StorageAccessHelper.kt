package com.fetchpro.downloadmanager.download.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles both legacy File API and SAF DocumentFile for custom dirs.
 * For multipart downloads, File API is required for RandomAccessFile.
 * SAF is supported for single-part or final move.
 */
@Singleton
class StorageAccessHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isSafUri(path: String): Boolean = path.startsWith("content://")

    fun getDocumentFileFromTreeUri(treeUriStr: String): DocumentFile? {
        return try {
            val uri = Uri.parse(treeUriStr)
            DocumentFile.fromTreeUri(context, uri)
        } catch (_: Exception) { null }
    }

    fun createFileInSafDir(treeUriStr: String, fileName: String, mimeType: String): DocumentFile? {
        val tree = getDocumentFileFromTreeUri(treeUriStr) ?: return null
        // Delete existing if any
        tree.findFile(fileName)?.delete()
        return tree.createFile(mimeType, fileName)
    }

    fun getOutputStreamForSaf(file: DocumentFile): OutputStream? {
        return try {
            context.contentResolver.openOutputStream(file.uri, "w")
        } catch (_: Exception) { null }
    }

    /**
     * For multipart, we must download to app's temp file (which supports RandomAccessFile)
     * then copy to SAF destination on completion.
     */
    fun copyFileToSaf(source: File, treeUriStr: String, fileName: String, mimeType: String): Boolean {
        return try {
            val docFile = createFileInSafDir(treeUriStr, fileName, mimeType) ?: return false
            context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                source.inputStream().use { inp -> inp.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resolveDisplayPath(pathOrUri: String): String {
        return if (isSafUri(pathOrUri)) {
            // Try to get display name from SAF
            try {
                val uri = Uri.parse(pathOrUri)
                DocumentFile.fromTreeUri(context, uri)?.name ?: pathOrUri
            } catch (_: Exception) { pathOrUri }
        } else pathOrUri
    }
}
