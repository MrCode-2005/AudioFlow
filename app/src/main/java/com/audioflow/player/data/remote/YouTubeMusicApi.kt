package com.audioflow.player.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubeMusicApi"

/**
 * A trending track from YouTube Music charts.
 * Contains all metadata needed for display + playback.
 */
data class TrendingTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: Long = 0L,       // in milliseconds
    val chartPosition: Int = 0,    // 1-indexed chart rank
    val views: String = "",        // "1.2M views" formatted string
    val isExplicit: Boolean = false
)

/**
 * YouTube Music InnerTube API client.
 *
 * Fetches real trending/chart data from YouTube Music by calling the same
 * internal API (`/youtubei/v1/browse`) that the official YouTube Music app uses.
 *
 * Country-specific charts are supported via ISO 3166-1 Alpha-2 codes (e.g., "IN", "US").
 */
@Singleton
class YouTubeMusicApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        
        // InnerTube browse IDs for charts
        private const val BROWSE_ID_CHARTS = "FEmusic_charts"
    }

    /**
     * Fetch trending songs from YouTube Music charts.
     *
     * @param countryCode ISO 3166-1 Alpha-2 country code (e.g., "IN" for India, "US" for USA).
     *                    Defaults to "IN".
     * @return List of trending tracks with chart positions, or an error.
     */
    suspend fun getTrendingSongs(countryCode: String = "IN"): Result<List<TrendingTrack>> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = buildChartsRequestBody(countryCode)
                
                val request = Request.Builder()
                    .url("$BASE_URL/browse?key=$API_KEY&prettyPrint=false")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip")
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("YouTube Music API request failed: HTTP ${response.code}")
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val tracks = parseChartsResponse(body)
                
                if (tracks.isEmpty()) {
                    Log.w(TAG, "No trending tracks parsed — trying fallback strategy")
                    val fallbackTracks = parseFallbackChartsResponse(body)
                    if (fallbackTracks.isNotEmpty()) {
                        return@withContext Result.success(fallbackTracks)
                    }
                }

                Log.d(TAG, "Fetched ${tracks.size} trending songs for country=$countryCode")
                Result.success(tracks)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch trending: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Build the InnerTube request body for the charts/browse endpoint.
     */
    private fun buildChartsRequestBody(countryCode: String): JSONObject {
        return JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20241023.01.00")
                    put("hl", "en")
                    put("gl", countryCode)
                    put("platform", "DESKTOP")
                    put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                })
                put("user", JSONObject().apply {
                    put("lockedSafetyMode", false)
                })
            })
            put("browseId", BROWSE_ID_CHARTS)
            put("formData", JSONObject().apply {
                put("selectedValues", JSONArray().put(countryCode))
            })
        }
    }

    /**
     * Parse the InnerTube charts response to extract trending tracks.
     * Navigates the deeply nested JSON structure to find musicResponsiveListItemRenderers.
     */
    private fun parseChartsResponse(responseBody: String): List<TrendingTrack> {
        val tracks = mutableListOf<TrendingTrack>()
        try {
            val json = JSONObject(responseBody)
            
            // Navigate: contents → singleColumnBrowseResultsRenderer → tabs → tabRenderer → content
            val tabs = json
                .optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?: return emptyList()

            // Iterate through all tabs looking for chart data
            for (tabIdx in 0 until tabs.length()) {
                val tabContent = tabs.optJSONObject(tabIdx)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                    ?: continue

                // Each section in the tab
                for (sectionIdx in 0 until tabContent.length()) {
                    val section = tabContent.optJSONObject(sectionIdx) ?: continue
                    
                    // Try musicCarouselShelfRenderer (horizontal lists)
                    val carouselItems = section
                        .optJSONObject("musicCarouselShelfRenderer")
                        ?.optJSONArray("contents")
                    
                    if (carouselItems != null) {
                        extractTracksFromItems(carouselItems, tracks)
                        if (tracks.size >= 20) break  // Got enough from first chart section
                    }
                    
                    // Try musicShelfRenderer (vertical lists)
                    val shelfItems = section
                        .optJSONObject("musicShelfRenderer")
                        ?.optJSONArray("contents")
                    
                    if (shelfItems != null) {
                        extractTracksFromItems(shelfItems, tracks)
                        if (tracks.size >= 20) break
                    }
                }
                
                if (tracks.isNotEmpty()) break  // Use first tab with results (usually "Top songs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing charts response: ${e.message}", e)
        }
        return tracks
    }

    /**
     * Extract track information from a JSON array of list items.
     */
    private fun extractTracksFromItems(items: JSONArray, tracks: MutableList<TrendingTrack>) {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            
            // Try musicResponsiveListItemRenderer (standard chart items)
            val renderer = item.optJSONObject("musicResponsiveListItemRenderer")
            if (renderer != null) {
                val track = parseResponsiveListItem(renderer, tracks.size + 1)
                if (track != null) tracks.add(track)
                continue
            }
            
            // Try musicTwoRowItemRenderer (carousel items)
            val twoRowRenderer = item.optJSONObject("musicTwoRowItemRenderer")
            if (twoRowRenderer != null) {
                val track = parseTwoRowItem(twoRowRenderer, tracks.size + 1)
                if (track != null) tracks.add(track)
            }
        }
    }

    /**
     * Parse a musicResponsiveListItemRenderer (used in vertical chart lists).
     */
    private fun parseResponsiveListItem(renderer: JSONObject, position: Int): TrendingTrack? {
        try {
            // Extract video ID from navigation endpoint or overlay
            val videoId = extractVideoId(renderer) ?: return null
            
            // Extract text columns (flexColumns)
            val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
            
            val title = extractTextFromFlexColumn(flexColumns, 0) ?: return null
            val artist = extractTextFromFlexColumn(flexColumns, 1) ?: "Unknown Artist"
            
            // Extract thumbnail
            val thumbnailUrl = extractThumbnail(renderer)
            
            // Extract chart position from the index column or rank badge
            val chartPos = extractChartPosition(renderer) ?: position
            
            return TrendingTrack(
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                chartPosition = chartPos,
                duration = 0L  // Duration not always in chart data
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse list item: ${e.message}")
            return null
        }
    }

    /**
     * Parse a musicTwoRowItemRenderer (used in horizontal carousels).
     */
    private fun parseTwoRowItem(renderer: JSONObject, position: Int): TrendingTrack? {
        try {
            // Video ID from navigation endpoint
            val videoId = renderer
                .optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            
            // Title
            val title = renderer
                .optJSONObject("title")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            
            // Artist (subtitle)
            val artist = renderer
                .optJSONObject("subtitle")
                ?.optJSONArray("runs")
                ?.let { runs ->
                    buildString {
                        for (r in 0 until runs.length()) {
                            val text = runs.optJSONObject(r)?.optString("text") ?: ""
                            if (text != " • " && text != " & " && !text.contains("views")) {
                                if (isNotEmpty() && text != ", ") append(", ")
                                append(text)
                            }
                        }
                    }
                }
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            
            // Thumbnail
            val thumbnails = renderer
                .optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            
            val thumbnailUrl = getBestThumbnail(thumbnails)
            
            return TrendingTrack(
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                chartPosition = position
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse two-row item: ${e.message}")
            return null
        }
    }

    // ==================== HELPER METHODS ====================

    private fun extractVideoId(renderer: JSONObject): String? {
        // Try overlay → musicItemThumbnailOverlayRenderer → content → musicPlayButtonRenderer
        val overlayVideoId = renderer
            .optJSONArray("overlay")
            ?.optJSONObject(0)
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
        
        if (overlayVideoId != null) return overlayVideoId
        
        // Try direct playlistItemData
        val playlistVideoId = renderer
            .optJSONObject("playlistItemData")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
        
        if (playlistVideoId != null) return playlistVideoId
        
        // Try flexColumns → navigationEndpoint
        val flexColumns = renderer.optJSONArray("flexColumns")
        if (flexColumns != null && flexColumns.length() > 0) {
            val navEndpoint = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            
            if (navEndpoint != null) return navEndpoint
        }
        
        return null
    }

    private fun extractTextFromFlexColumn(flexColumns: JSONArray, index: Int): String? {
        if (index >= flexColumns.length()) return null
        return flexColumns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.let { runs ->
                buildString {
                    for (r in 0 until runs.length()) {
                        val text = runs.optJSONObject(r)?.optString("text") ?: ""
                        // Skip separator dots and view counts
                        if (text != " • " && text != " · " && !text.contains("plays") && !text.contains("views")) {
                            append(text)
                        }
                    }
                }.trim().takeIf { it.isNotBlank() }
            }
    }

    private fun extractThumbnail(renderer: JSONObject): String {
        val thumbnails = renderer
            .optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        
        return getBestThumbnail(thumbnails)
    }

    /**
     * Get the highest quality thumbnail URL available.
     */
    private fun getBestThumbnail(thumbnails: JSONArray?): String {
        if (thumbnails == null || thumbnails.length() == 0) return ""
        
        var bestUrl = ""
        var bestWidth = 0
        
        for (i in 0 until thumbnails.length()) {
            val thumb = thumbnails.optJSONObject(i) ?: continue
            val width = thumb.optInt("width", 0)
            val url = thumb.optString("url", "")
            if (width > bestWidth && url.isNotBlank()) {
                bestWidth = width
                bestUrl = url
            }
        }
        
        // Fallback to last thumbnail if width parsing failed
        if (bestUrl.isBlank()) {
            bestUrl = thumbnails.optJSONObject(thumbnails.length() - 1)
                ?.optString("url", "") ?: ""
        }
        
        return bestUrl
    }

    private fun extractChartPosition(renderer: JSONObject): Int? {
        // Try customIndexColumn
        return renderer
            .optJSONObject("customIndexColumn")
            ?.optJSONObject("musicCustomIndexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
            ?.toIntOrNull()
    }

    /**
     * Fallback parser: recursively search for watchEndpoint videoIds in the JSON.
     * Used when the primary parser can't find structured chart data.
     */
    private fun parseFallbackChartsResponse(responseBody: String): List<TrendingTrack> {
        val tracks = mutableListOf<TrendingTrack>()
        try {
            val json = JSONObject(responseBody)
            findTracksRecursive(json, tracks, maxTracks = 25)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback parse failed: ${e.message}", e)
        }
        return tracks
    }

    /**
     * Recursively search JSON for musicResponsiveListItemRenderer or
     * musicTwoRowItemRenderer nodes containing playable tracks.
     */
    private fun findTracksRecursive(obj: JSONObject, tracks: MutableList<TrendingTrack>, maxTracks: Int) {
        if (tracks.size >= maxTracks) return
        
        // Check if this object is a musicResponsiveListItemRenderer
        val responsiveRenderer = obj.optJSONObject("musicResponsiveListItemRenderer")
        if (responsiveRenderer != null) {
            val track = parseResponsiveListItem(responsiveRenderer, tracks.size + 1)
            if (track != null && tracks.none { it.videoId == track.videoId }) {
                tracks.add(track)
            }
        }
        
        // Check if this object is a musicTwoRowItemRenderer
        val twoRowRenderer = obj.optJSONObject("musicTwoRowItemRenderer")
        if (twoRowRenderer != null) {
            val track = parseTwoRowItem(twoRowRenderer, tracks.size + 1)
            if (track != null && tracks.none { it.videoId == track.videoId }) {
                tracks.add(track)
            }
        }
        
        // Recurse into all nested objects and arrays
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = obj.opt(key)) {
                is JSONObject -> findTracksRecursive(value, tracks, maxTracks)
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        when (val element = value.opt(i)) {
                            is JSONObject -> findTracksRecursive(element, tracks, maxTracks)
                        }
                    }
                }
            }
        }
    }
}
