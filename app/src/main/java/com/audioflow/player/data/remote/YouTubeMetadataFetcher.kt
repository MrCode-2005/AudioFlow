package com.audioflow.player.data.remote

import com.audioflow.player.model.YouTubeMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMetadataFetcher @Inject constructor() {
    
    companion object {
        private val YOUTUBE_PATTERNS = listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})"),
            Pattern.compile("(?:https?://)?(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Pattern.compile("(?:https?://)?(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})"),
            Pattern.compile("(?:https?://)?(?:www\\.)?youtube\\.com/v/([a-zA-Z0-9_-]{11})"),
            Pattern.compile("(?:https?://)?(?:m\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})")
        )
        
        private const val OEMBED_URL = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=%s&format=json"
    }
    
    /**
     * Extracts video ID from various YouTube URL formats
     */
    fun extractVideoId(url: String): String? {
        for (pattern in YOUTUBE_PATTERNS) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
    
    /**
     * Validates if a string is a valid YouTube URL
     */
    fun isValidYouTubeUrl(url: String): Boolean {
        return extractVideoId(url) != null
    }
    
    /**
     * Fetches metadata for a YouTube video using the oEmbed API (public, no API key required)
     */
    suspend fun fetchMetadata(videoIdOrUrl: String): Result<YouTubeMetadata> = withContext(Dispatchers.IO) {
        try {
            val videoId = if (videoIdOrUrl.length == 11 && !videoIdOrUrl.contains("/")) {
                videoIdOrUrl
            } else {
                extractVideoId(videoIdOrUrl) ?: return@withContext Result.failure(
                    IllegalArgumentException("Invalid YouTube URL")
                )
            }
            
            val url = URL(OEMBED_URL.format(videoId))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val metadata = YouTubeMetadata(
                        videoId = videoId,
                        title = json.optString("title", "Unknown Title"),
                        author = json.optString("author_name", "Unknown Channel"),
                        thumbnailUrl = json.optString("thumbnail_url", getThumbnailUrl(videoId))
                    )
                    
                    Result.success(metadata)
                } else {
                    Result.failure(Exception("Failed to fetch metadata: HTTP ${connection.responseCode}"))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets the thumbnail URL for a video (high quality)
     */
    fun getThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }
    
    /**
     * Gets various thumbnail quality options
     */
    fun getThumbnailUrls(videoId: String): Map<String, String> {
        return mapOf(
            "default" to "https://img.youtube.com/vi/$videoId/default.jpg",
            "medium" to "https://img.youtube.com/vi/$videoId/mqdefault.jpg",
            "high" to "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
            "standard" to "https://img.youtube.com/vi/$videoId/sddefault.jpg",
            "maxres" to "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        )
    }
}
