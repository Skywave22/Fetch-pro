package com.fetchpro.downloadmanager.domain.model

/**
 * Advanced profiles for sites - ADM feature
 * Allows custom headers, User-Agent, proxy, referer, cookie per site
 */
data class SiteProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val hostPattern: String, // e.g., "example.com" or "*.example.com"
    val customHeaders: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val referer: String? = null,
    val proxyConfigId: String? = null, // link to proxy config
    val cookies: String? = null,
    val enabled: Boolean = true
) {
    fun matches(url: String): Boolean {
        return try {
            val host = java.net.URL(url).host
            when {
                hostPattern.startsWith("*.") -> {
                    val domain = hostPattern.removePrefix("*.")
                    host == domain || host.endsWith(".$domain")
                }
                else -> host == hostPattern || host.contains(hostPattern)
            }
        } catch (_: Exception) {
            url.contains(hostPattern)
        }
    }
}

data class CustomFilenameTemplate(
    val template: String = "%(title)s.%(ext)s",
    val variables: Map<String, String> = mapOf(
        "%(title)s" to "Video title",
        "%(id)s" to "Video ID",
        "%(ext)s" to "File extension",
        "%(uploader)s" to "Uploader name",
        "%(upload_date)s" to "Upload date",
        "%(resolution)s" to "Resolution",
        "%(width)s" to "Width",
        "%(height)s" to "Height"
    )
) {
    fun apply(values: Map<String, String>): String {
        var result = template
        values.forEach { (key, value) ->
            result = result.replace(key, value)
        }
        // Sanitize filename
        return result.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    companion object {
        val PRESETS = listOf(
            "%(title)s.%(ext)s",
            "%(uploader)s - %(title)s.%(ext)s",
            "%(title)s [%(id)s].%(ext)s",
            "%(upload_date)s - %(title)s.%(ext)s",
            "%(resolution)s - %(title)s.%(ext)s"
        )
    }
}
