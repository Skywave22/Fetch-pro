package com.fetchpro.downloadmanager.browser.adblock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple AdBlocker for built-in browser - enhances browsing experience
 * Uses hosts file style blocking (like 1DM+ ad-free experience)
 */
@Singleton
class AdBlocker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val blockedHosts = mutableSetOf<String>()
    private var isInitialized = false

    companion object {
        // Common ad domains (small subset, full list would be fetched remotely)
        private val DEFAULT_BLOCKED = listOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "adservice.google.com",
            "facebook.net",
            "fbcdn.net",
            "analytics.google.com",
            "googletagmanager.com",
            "googletagservices.com",
            "amazon-adsystem.com",
            "adsystem.amazon.com",
            "adnxs.com",
            "criteo.net",
            "criteo.com",
            "scorecardresearch.com",
            "hotjar.com",
            "mixpanel.com",
            "segment.com",
            "taboola.com",
            "outbrain.com"
        )
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        blockedHosts.addAll(DEFAULT_BLOCKED)
        
        // Try to load from assets if available
        try {
            val assetManager = context.assets
            val files = assetManager.list("") ?: emptyArray()
            if ("adblock_hosts.txt" in files) {
                assetManager.open("adblock_hosts.txt").use { input ->
                    BufferedReader(InputStreamReader(input)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line!!.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                blockedHosts.add(trimmed.lowercase())
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        
        isInitialized = true
    }

    fun shouldBlock(url: String): Boolean {
        if (!isInitialized) return false
        val lowerUrl = url.lowercase()
        return blockedHosts.any { host ->
            lowerUrl.contains(host)
        }
    }

    fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/ads/") ||
                lower.contains("/ad/") ||
                lower.contains("advert") ||
                lower.contains("doubleclick") ||
                lower.contains("googlesyndication") ||
                lower.contains("banner")
    }

    fun addCustomBlockedHost(host: String) {
        blockedHosts.add(host.lowercase())
    }

    fun removeBlockedHost(host: String) {
        blockedHosts.remove(host.lowercase())
    }

    fun getBlockedCount(): Int = blockedHosts.size
}
