package com.fetchpro.downloadmanager.download.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SponsorBlock integration - Seal feature
 * Fetches sponsor segments from https://sponsor.ajay.app and can skip them via ffmpeg
 */
@Singleton
class SponsorBlockManager @Inject constructor(
    private val client: OkHttpClient
) {

    data class SponsorSegment(
        val segment: Pair<Double, Double>, // start, end in seconds
        val uuid: String,
        val category: String // sponsor, intro, outro, interaction, selfpromo, music_offtopic
    )

    suspend fun getSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        try {
            // SponsorBlock API: https://sponsor.ajay.app/api/skipSegments?videoID=xxx
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList<SponsorSegment>()

            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(body)
            val segments = mutableListOf<SponsorSegment>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val segmentArray = obj.getJSONArray("segment")
                val start = segmentArray.getDouble(0)
                val end = segmentArray.getDouble(1)
                val uuid = obj.getString("UUID")
                val category = obj.optString("category", "sponsor")
                segments.add(SponsorSegment(Pair(start, end), uuid, category))
            }

            segments
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Generate ffmpeg filter to skip sponsor segments
     * Example: -vf "select='not(between(t,10,20)+between(t,30,40))',setpts=N/FRAME_RATE/TB" -af "aselect='not(between(t,10,20)+between(t,30,40))',asetpts=N/SR/TB"
     */
    fun generateFfmpegFilter(segments: List<SponsorSegment>): String? {
        if (segments.isEmpty()) return null

        val conditions = segments.joinToString("+") { (start, end) ->
            "between(t,$start,$end)"
        }

        return "select='not($conditions)',setpts=N/FRAME_RATE/TB"
    }

    fun shouldSkipCategory(category: String, userPrefs: Set<String>): Boolean {
        // User can choose which categories to skip: sponsor, intro, outro, interaction, selfpromo, music_offtopic
        return category in userPrefs
    }
}
