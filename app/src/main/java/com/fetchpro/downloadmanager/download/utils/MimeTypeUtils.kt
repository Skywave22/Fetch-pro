package com.fetchpro.downloadmanager.download.utils

import android.webkit.MimeTypeMap
import java.net.URLConnection

object MimeTypeUtils {

    fun guessMimeType(fileName: String?, contentType: String?): String? {
        if (!contentType.isNullOrBlank() && contentType != "application/octet-stream") {
            return contentType.substringBefore(';').trim()
        }
        if (fileName != null) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            }
        }
        return contentType ?: URLConnection.guessContentTypeFromName(fileName)
    }

    fun getExtensionFromMimeType(mimeType: String?): String? {
        if (mimeType == null) return null
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    }
}
