package com.audioflow.player.data.remote

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubeExtractor"

/**
 * YouTube search result before stream extraction
 */
data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: Long // in milliseconds
)

/**
 * Full stream info after extraction
 */
data class YouTubeStreamInfo(
    val videoId: String,
    val audioStreamUrl: String,
    val mimeType: String,
    val bitrate: Int,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: Long
)

/**
 * YouTube playlist info
 */
data class YouTubePlaylistInfo(
    val playlistId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val videoCount: Int,
    val videos: List<YouTubeSearchResult>
)

/**
 * Video stream info for background video playback
 */
data class YouTubeVideoStreamInfo(
    val videoId: String,
    val videoStreamUrl: String,
    val audioStreamUrl: String?,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: Long
)

/**
 * YouTube extractor using yt-dlp via youtubedl-android library.
 * Provides reliable YouTube search and audio stream extraction.
 */
@Singleton
class YouTubeExtractor @Inject constructor(
    private val cookieManager: YouTubeCookieManager
) {
    
    // Custom exception types for better error handling
    sealed class YouTubeError : Exception() {
        object ServiceUnavailable : YouTubeError()
        object RateLimited : YouTubeError()
        data class VideoUnavailable(override val message: String) : YouTubeError()
        data class NetworkError(override val message: String) : YouTubeError()
    }
    
    // OkHttp client for URL validation (same config as MusicService)
    private val validationClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    
    /**
     * Validate that a stream URL is actually reachable.
     * Returns true if URL returns 200-299 status, false otherwise.
     */
    private fun validateStreamUrl(url: String): Boolean {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", YouTubeCookieManager.USER_AGENT)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=0-1") // Request first 2 bytes only
                .build()
            
            val response = validationClient.newCall(request).execute()
            val code = response.code
            val contentType = response.header("Content-Type")
            val contentLength = response.header("Content-Length")
            
            Log.d(TAG, "URL validation: code=$code, contentType=$contentType, contentLength=$contentLength")
            response.close()
            
            // Accept 200 (OK), 206 (Partial Content), or 302/303 (Redirects handled by OkHttp)
            return code in 200..299 || code == 206
        } catch (e: Exception) {
            Log.e(TAG, "URL validation failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Search YouTube for videos matching the query using yt-dlp
     */
    suspend fun search(query: String, maxResults: Int = 20): Result<List<YouTubeSearchResult>> = 
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Searching YouTube for: $query")
            
            // Wait for yt-dlp initialization (with timeout)
            var attempts = 0
            while (!com.audioflow.player.AudioFlowApp.isYtDlpReady && attempts < 30) {
                delay(100)
                attempts++
            }
            
            if (!com.audioflow.player.AudioFlowApp.isYtDlpReady) {
                Log.e(TAG, "yt-dlp not initialized after timeout")
                return@withContext Result.failure(YouTubeError.ServiceUnavailable)
            }
            
            try {
                // Use yt-dlp search syntax: ytsearchN:query
                val searchQuery = "ytsearch$maxResults:$query"
                
                val request = YoutubeDLRequest(searchQuery)
                request.addOption("--flat-playlist") // Get metadata only, don't extract streams
                request.addOption("--no-download")
                request.addOption("-j") // JSON output
                request.addOption("--no-warnings")
                request.addOption("--ignore-errors")
                
                // Get the output
                val response = YoutubeDL.getInstance().execute(request)
                val output = response.out
                
                if (output.isNullOrBlank()) {
                    Log.w(TAG, "Empty response from yt-dlp search")
                    return@withContext Result.failure(YouTubeError.ServiceUnavailable)
                }
                
                // Parse JSON lines (each line is a separate video)
                val results = mutableListOf<YouTubeSearchResult>()
                output.lines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val json = org.json.JSONObject(line)
                        val videoId = json.optString("id", "")
                        if (videoId.isEmpty()) return@forEach
                        
                        val title = json.optString("title", "Unknown Title")
                        val uploader = json.optString("uploader", json.optString("channel", "Unknown Artist"))
                        val duration = json.optLong("duration", 0) * 1000 // Convert to ms
                        
                        // Get thumbnail URL
                        val thumbnail = json.optString("thumbnail", "")
                            .ifEmpty { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" }
                        
                        results.add(YouTubeSearchResult(
                            videoId = videoId,
                            title = title,
                            artist = uploader,
                            thumbnailUrl = thumbnail,
                            duration = duration
                        ))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse search result: ${e.message}")
                    }
                }
                
                Log.d(TAG, "Found ${results.size} search results")
                
                // Filter out videos longer than 10 minutes (likely mixes/compilations)
                val maxDurationMs = 10 * 60 * 1000L // 10 minutes
                val filteredResults = results.filter { it.duration in 1..maxDurationMs }
                Log.d(TAG, "Filtered to ${filteredResults.size} results (< 10 min duration)")
                
                Result.success(filteredResults.take(maxResults))
                
            } catch (e: Exception) {
                Log.e(TAG, "Search failed: ${e.message}", e)
                when {
                    e.message?.contains("429") == true -> Result.failure(YouTubeError.RateLimited)
                    e.message?.contains("network") == true -> Result.failure(YouTubeError.NetworkError(e.message ?: "Network error"))
                    else -> Result.failure(YouTubeError.ServiceUnavailable)
                }
            }
        }
    
    /**
     * Check if URL is a YouTube playlist
     */
    fun isPlaylistUrl(url: String): Boolean {
        return url.contains("youtube.com/playlist") ||
               url.contains("list=")
    }
    
    /**
     * Extract all videos from a YouTube playlist using yt-dlp
     */
    suspend fun extractPlaylist(playlistUrl: String): Result<YouTubePlaylistInfo> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Extracting playlist: $playlistUrl")
            
            // Wait for yt-dlp initialization
            var attempts = 0
            while (!com.audioflow.player.AudioFlowApp.isYtDlpReady && attempts < 30) {
                delay(100)
                attempts++
            }
            
            if (!com.audioflow.player.AudioFlowApp.isYtDlpReady) {
                return@withContext Result.failure(YouTubeError.ServiceUnavailable)
            }
            
            try {
                val request = YoutubeDLRequest(playlistUrl)
                request.addOption("--flat-playlist") // Get metadata only
                request.addOption("--no-download")
                request.addOption("-j") // JSON output
                request.addOption("--no-warnings")
                request.addOption("--ignore-errors")
                
                val response = YoutubeDL.getInstance().execute(request)
                val output = response.out
                
                if (output.isNullOrBlank()) {
                    return@withContext Result.failure(YouTubeError.ServiceUnavailable)
                }
                
                // Parse JSON lines
                val videos = mutableListOf<YouTubeSearchResult>()
                var playlistTitle = "Unknown Playlist"
                var playlistId = ""
                var channelName = "Unknown"
                var playlistThumb = ""
                
                output.lines().forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val json = org.json.JSONObject(line)
                        
                        // Get playlist metadata from first entry
                        if (playlistId.isEmpty()) {
                            playlistId = json.optString("playlist_id", "")
                            playlistTitle = json.optString("playlist_title", "Playlist")
                            channelName = json.optString("playlist_uploader", "Unknown")
                        }
                        
                        val videoId = json.optString("id", "")
                        if (videoId.isEmpty()) return@forEach
                        
                        val title = json.optString("title", "Unknown Title")
                        val uploader = json.optString("uploader", json.optString("channel", channelName))
                        val duration = json.optLong("duration", 0) * 1000
                        
                        val thumbnail = json.optString("thumbnail", "")
                            .ifEmpty { "https://img.youtube.com/vi/$videoId/hqdefault.jpg" }
                        
                        if (playlistThumb.isEmpty()) playlistThumb = thumbnail
                        
                        videos.add(YouTubeSearchResult(
                            videoId = videoId,
                            title = title,
                            artist = uploader,
                            thumbnailUrl = thumbnail,
                            duration = duration
                        ))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse playlist entry: ${e.message}")
                    }
                }
                
                Log.d(TAG, "Extracted ${videos.size} videos from playlist")
                
                Result.success(YouTubePlaylistInfo(
                    playlistId = playlistId,
                    title = playlistTitle,
                    channelName = channelName,
                    thumbnailUrl = playlistThumb,
                    videoCount = videos.size,
                    videos = videos
                ))
                
            } catch (e: Exception) {
                Log.e(TAG, "Playlist extraction failed: ${e.message}", e)
                Result.failure(YouTubeError.ServiceUnavailable)
            }
        }
    
    /**
     * Extract the audio stream URL for a video using yt-dlp
     * Includes retry logic for reliability on real devices
     */
    suspend fun extractStreamUrl(videoId: String): Result<YouTubeStreamInfo> = 
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Extracting stream for: $videoId")
            
            // Wait for yt-dlp initialization (with timeout)
            var attempts = 0
            while (!com.audioflow.player.AudioFlowApp.isYtDlpReady && attempts < 50) {
                delay(100)
                attempts++
            }
            
            if (!com.audioflow.player.AudioFlowApp.isYtDlpReady) {
                Log.e(TAG, "yt-dlp not initialized after timeout")
                return@withContext Result.failure(YouTubeError.ServiceUnavailable)
            }
            
            // Retry logic for better reliability on real devices
            var lastException: Exception? = null
            repeat(3) { retryAttempt ->
                try {
                    val result = extractStreamUrlInternal(videoId, retryAttempt)
                    if (result.isSuccess) {
                        return@withContext result
                    }
                    lastException = result.exceptionOrNull() as? Exception
                    
                    // Don't retry for permanent errors
                    val error = result.exceptionOrNull()
                    if (error is YouTubeError.VideoUnavailable) {
                        return@withContext result
                    }
                    
                    // Wait before retry with exponential backoff
                    if (retryAttempt < 2) {
                        val delayMs = (retryAttempt + 1) * 500L
                        Log.d(TAG, "Retrying extraction in ${delayMs}ms (attempt ${retryAttempt + 2}/3)")
                        delay(delayMs)
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "Extraction attempt ${retryAttempt + 1} failed: ${e.message}")
                    if (retryAttempt < 2) {
                        delay((retryAttempt + 1) * 500L)
                    }
                }
            }
            
            Result.failure(lastException ?: YouTubeError.ServiceUnavailable)
        }
    
    /**
     * Internal extraction logic
     */
    private suspend fun extractStreamUrlInternal(videoId: String, attempt: Int): Result<YouTubeStreamInfo> {
        try {
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val request = YoutubeDLRequest(videoUrl)
            
            // Essential options for reliable extraction
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--no-check-certificate") // Helps on some devices with cert issues
            request.addOption("--extractor-retries", "3") // Retry individual extractors
            request.addOption("--socket-timeout", "30") // Reasonable timeout
            
            // CRITICAL: Use Android player client to get mobile-friendly URLs
            // This generates URLs signed for Android devices, avoiding 403 errors
            request.addOption("--extractor-args", "youtube:player_client=android")
            
            // Use Android YouTube app User-Agent for consistency with player_client=android
            val androidUserAgent = "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip"
            request.addOption("--user-agent", androidUserAgent)
            
            // Add cookies if available (for age-restricted/premium content)
            val cookies = cookieManager.getCookies()
            if (cookies.isNotEmpty()) {
                Log.d(TAG, "Adding cookies to yt-dlp request")
                request.addOption("--add-header", "Cookie:$cookies")
            }
            
            // ALWAYS request audio-only format for reliable device playback
            // Priority: m4a (best compatibility) > webm/opus > any audio
            // Using explicit filtering to avoid DASH-only streams
            val formatString = when (attempt) {
                0 -> "bestaudio[ext=m4a][acodec!=none]/bestaudio[ext=webm][acodec!=none]/bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best"
                1 -> "worstaudio[ext=m4a]/worstaudio[ext=webm]/worstaudio"
                else -> "bestaudio/best"
            }
            request.addOption("-f", formatString)
            Log.d(TAG, "Extraction attempt $attempt with format: $formatString")
            
            // Get video info
            val videoInfo = YoutubeDL.getInstance().getInfo(request)
            
            if (videoInfo == null) {
                Log.e(TAG, "Failed to get video info for: $videoId")
                return Result.failure(YouTubeError.VideoUnavailable("Could not extract video info"))
            }
            
            // Try to get URL from various sources
            var streamUrl = videoInfo.url
            var format: String? = videoInfo.ext
            
            Log.d(TAG, "VideoInfo - url: ${if (streamUrl.isNullOrBlank()) "null" else "present (${streamUrl?.length} chars)"}")
            Log.d(TAG, "VideoInfo - ext: $format, manifestUrl: ${if (videoInfo.manifestUrl.isNullOrBlank()) "null" else "present"}")
            
            // Check requestedFormats first - this usually has the direct audio URL
            if (streamUrl.isNullOrBlank()) {
                val requestedFormats = videoInfo.requestedFormats
                Log.d(TAG, "RequestedFormats: ${requestedFormats?.size ?: 0} formats")
                
                if (requestedFormats != null && requestedFormats.isNotEmpty()) {
                    // Find audio-only format (no video codec)
                    val audioFormat = requestedFormats.find { 
                        (it.vcodec == "none" || it.vcodec == null) && !it.url.isNullOrBlank()
                    } ?: requestedFormats.find { !it.url.isNullOrBlank() }
                    
                    audioFormat?.let {
                        streamUrl = it.url
                        format = it.ext
                        Log.d(TAG, "Found format from requestedFormats: ${it.formatId}, ext: ${it.ext}")
                    }
                }
            }
            
            // DO NOT use manifest URLs - ExoPlayer would need DASH/HLS source factory
            // which is not compatible with our progressive streaming setup
            if (streamUrl.isNullOrBlank() && !videoInfo.manifestUrl.isNullOrBlank()) {
                Log.w(TAG, "WARNING: Only manifest URL available (DASH/HLS), not direct stream. Rejecting.")
                Log.w(TAG, "Manifest URL: ${videoInfo.manifestUrl?.take(60)}...")
                // Don't use it - it will fail with progressive source
            }
            
            // Additional check: reject manifest URLs disguised as stream URLs
            if (!streamUrl.isNullOrBlank() && 
                (streamUrl!!.contains(".mpd") || streamUrl!!.contains(".m3u") || streamUrl!!.contains("manifest"))) {
                Log.w(TAG, "Rejecting manifest URL masquerading as stream: ${streamUrl?.take(60)}")
                streamUrl = null
            }
            
            if (streamUrl.isNullOrBlank()) {
                Log.e(TAG, "No direct stream URL found for: $videoId")
                return Result.failure(YouTubeError.VideoUnavailable("No direct audio stream available. Video may require special handling."))
            }
            
            val title = videoInfo.title ?: "Unknown Title"
            val artist = videoInfo.uploader ?: "Unknown Artist"
            val duration = (videoInfo.duration?.toLong() ?: 0L) * 1000 // Convert to ms
            val thumbnail = videoInfo.thumbnail 
                ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            
            // Determine mime type from format
            val mimeType = when {
                format?.contains("opus") == true -> "audio/opus"
                format?.contains("webm") == true -> "audio/webm"
                format?.contains("m4a") == true -> "audio/mp4"
                else -> "audio/mp4"
            }
            
            val bitrate = 128 // Default bitrate
            
            // Log detailed URL info for debugging on devices
            val urlInfo = if (!streamUrl.isNullOrBlank()) {
                val prefix = streamUrl!!.take(80)
                val hasRn = streamUrl!!.contains("&rn=")
                val hasRange = streamUrl!!.contains("&range=")
                val hasGooglevideo = streamUrl!!.contains("googlevideo.com")
                "prefix=$prefix..., hasRn=$hasRn, hasRange=$hasRange, isGooglevideo=$hasGooglevideo"
            } else "NULL URL"
            
            Log.d(TAG, "Stream URL extracted: $urlInfo")
            
            // Validate URL is reachable (do this in background - don't block on failure)
            val isUrlValid = try {
                validateStreamUrl(streamUrl!!)
            } catch (e: Exception) {
                Log.w(TAG, "URL validation exception: ${e.message}")
                true // Proceed anyway if validation fails
            }
            Log.d(TAG, "URL validation result: $isUrlValid")
            
            if (!isUrlValid) {
                Log.w(TAG, "WARNING: Stream URL failed validation but proceeding anyway")
            }
            
            Log.d(TAG, "Successfully extracted stream for: $title (URL length: ${streamUrl?.length})")
            
            return Result.success(YouTubeStreamInfo(
                videoId = videoId,
                audioStreamUrl = streamUrl!!,
                mimeType = mimeType,
                bitrate = bitrate,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnail,
                duration = duration
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Stream extraction failed: ${e.message}", e)
            return when {
                e.message?.contains("Video unavailable") == true ||
                e.message?.contains("Private video") == true ||
                e.message?.contains("removed") == true ->
                    Result.failure(YouTubeError.VideoUnavailable(e.message ?: "Video unavailable"))
                e.message?.contains("429") == true -> 
                    Result.failure(YouTubeError.RateLimited)
                else -> 
                    Result.failure(YouTubeError.ServiceUnavailable)
            }
        }
    }
    
    /**
     * Extract video stream URL for background video playback
     * Returns both video and audio streams for ExoPlayer
     */
    suspend fun extractVideoStream(videoId: String): Result<YouTubeVideoStreamInfo> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Extracting video stream for: $videoId")
            
            // Wait for yt-dlp initialization
            var attempts = 0
            while (!com.audioflow.player.AudioFlowApp.isYtDlpReady && attempts < 30) {
                delay(100)
                attempts++
            }
            
            if (!com.audioflow.player.AudioFlowApp.isYtDlpReady) {
                return@withContext Result.failure(YouTubeError.ServiceUnavailable)
            }
            
            try {
                val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                val request = YoutubeDLRequest(videoUrl)
                request.addOption("--no-playlist")
                // Request best video + audio for background playback
                request.addOption("-f", "best[height<=720]/bestvideo[height<=720]+bestaudio/best")
                
                val videoInfo = YoutubeDL.getInstance().getInfo(request)
                
                if (videoInfo == null) {
                    return@withContext Result.failure(YouTubeError.VideoUnavailable("Could not extract video info"))
                }
                
                var videoStreamUrl: String? = null
                var audioStreamUrl: String? = null
                var width = 1280
                var height = 720
                
                // Try to get combined stream first
                if (!videoInfo.url.isNullOrBlank()) {
                    videoStreamUrl = videoInfo.url
                }
                
                // Otherwise check requested formats for separate streams
                if (videoStreamUrl.isNullOrBlank()) {
                    val requestedFormats = videoInfo.requestedFormats
                    if (requestedFormats != null && requestedFormats.isNotEmpty()) {
                        // Find video format
                        val videoFormat = requestedFormats.find { 
                            it.vcodec != null && it.vcodec != "none"
                        }
                        // Find audio format
                        val audioFormat = requestedFormats.find {
                            it.acodec != null && it.acodec != "none" && (it.vcodec == null || it.vcodec == "none")
                        }
                        
                        videoFormat?.let {
                            videoStreamUrl = it.url
                            width = it.width?.toInt() ?: 1280
                            height = it.height?.toInt() ?: 720
                        }
                        audioFormat?.let {
                            audioStreamUrl = it.url
                        }
                    }
                }
                
                // Fallback to manifest URL
                if (videoStreamUrl.isNullOrBlank() && !videoInfo.manifestUrl.isNullOrBlank()) {
                    videoStreamUrl = videoInfo.manifestUrl
                }
                
                if (videoStreamUrl.isNullOrBlank()) {
                    return@withContext Result.failure(YouTubeError.VideoUnavailable("No video stream available"))
                }
                
                val title = videoInfo.title ?: "Unknown Title"
                val artist = videoInfo.uploader ?: "Unknown Artist"
                val duration = (videoInfo.duration?.toLong() ?: 0L) * 1000
                val thumbnail = videoInfo.thumbnail ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                
                Result.success(YouTubeVideoStreamInfo(
                    videoId = videoId,
                    videoStreamUrl = videoStreamUrl!!,
                    audioStreamUrl = audioStreamUrl,
                    mimeType = "video/mp4",
                    width = width,
                    height = height,
                    title = title,
                    artist = artist,
                    thumbnailUrl = thumbnail,
                    duration = duration
                ))
                
            } catch (e: Exception) {
                Log.e(TAG, "Video stream extraction failed: ${e.message}", e)
                Result.failure(YouTubeError.ServiceUnavailable)
            }
        }
    
    /**
     * Check if YouTube service is available (yt-dlp is initialized)
     */
    suspend fun isServiceAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Try a simple version check to verify yt-dlp is working
            val request = YoutubeDLRequest("--version")
            YoutubeDL.getInstance().execute(request)
            true
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp not available: ${e.message}")
            false
        }
    }
    
    /**
     * Update yt-dlp binary to latest version (call periodically to handle YouTube changes)
     */
    suspend fun updateYtDlp(context: android.content.Context): Result<String> = 
        withContext(Dispatchers.IO) {
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(
                    context,
                    com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.STABLE
                )
                when (status) {
                    com.yausername.youtubedl_android.YoutubeDL.UpdateStatus.DONE -> {
                        Log.d(TAG, "yt-dlp updated successfully")
                        Result.success("yt-dlp updated successfully")
                    }
                    com.yausername.youtubedl_android.YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                        Log.d(TAG, "yt-dlp already up to date")
                        Result.success("Already up to date")
                    }
                    else -> {
                        Result.failure(Exception("Update failed: $status"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update yt-dlp: ${e.message}")
                Result.failure(e)
            }
        }
    
    /**
     * Reset instance health status (no-op, kept for API compatibility)
     */
    fun resetInstanceHealth() {
        Log.d(TAG, "resetInstanceHealth called (no-op with yt-dlp)")
    }
}
