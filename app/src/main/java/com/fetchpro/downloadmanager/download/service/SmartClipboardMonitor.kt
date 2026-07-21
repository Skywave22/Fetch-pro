package com.fetchpro.downloadmanager.download.service

import android.content.ClipboardManager
import android.content.Context
import android.content.ClipboardManager.OnPrimaryClipChangedListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart download option from 1DM+: monitors clipboard for downloadable links
 * and emits them as Flow. User can enable auto-download in settings.
 */
@Singleton
class SmartClipboardMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun observeClipboard(): Flow<String> = callbackFlow {
        val listener = OnPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: return@OnPrimaryClipChangedListener
                val url = extractUrl(text) ?: return@OnPrimaryClipChangedListener
                trySend(url)
            }
        }

        clipboardManager.addPrimaryClipChangedListener(listener)

        // Send current clipboard if it contains URL
        val currentClip = clipboardManager.primaryClip
        if (currentClip != null && currentClip.itemCount > 0) {
            currentClip.getItemAt(0).text?.toString()?.let { text ->
                extractUrl(text)?.let { trySend(it) }
            }
        }

        awaitClose {
            clipboardManager.removePrimaryClipChangedListener(listener)
        }
    }

    private fun extractUrl(text: String): String? {
        if (text.length > 2048) return null // Avoid huge clipboard
        val regex = Regex("(https?://\\S+)")
        val match = regex.find(text) ?: return null
        val url = match.value.trimEnd('.', ',', ')', ']', '"', '\'')
        return if (isDownloadableUrl(url)) url else null
    }

    private fun isDownloadableUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        // Exclude common non-downloadable domains for smart detection
        val excluded = listOf("youtube.com", "youtu.be", "play.google.com")
        if (excluded.any { url.contains(it) }) return false
        return true
    }
}
