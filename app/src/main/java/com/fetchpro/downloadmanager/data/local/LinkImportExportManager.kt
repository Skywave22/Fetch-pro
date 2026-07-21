package com.fetchpro.downloadmanager.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Import/Export download links from text file - feature from 1DM+
 * Supports one URL per line, ignores empty lines and comments starting with #
 */
@Singleton
class LinkImportExportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun importLinksFromUri(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.isEmpty()) continue
                        if (trimmed.startsWith("#")) continue
                        // Extract URL via regex, allow surrounding text
                        val url = extractFirstUrl(trimmed) ?: continue
                        urls.add(url)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        urls.distinct()
    }

    suspend fun exportLinksToUri(uri: Uri, links: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(links.joinToString("\n").toByteArray())
                output.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun extractFirstUrl(text: String): String? {
        val regex = Regex("(https?://\\S+)")
        return regex.find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
    }

    fun extractAllUrls(text: String): List<String> {
        val regex = Regex("(https?://\\S+)")
        return regex.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '"', '\'') }.toList()
    }
}
