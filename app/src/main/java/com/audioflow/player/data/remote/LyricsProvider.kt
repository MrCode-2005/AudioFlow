package com.audioflow.player.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides song lyrics from external APIs
 */
@Singleton
class LyricsProvider @Inject constructor() {
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    companion object {
        // Free lyrics API (lrclib.net)
        private const val LRCLIB_API = "https://lrclib.net/api/get"
    }
    
    /**
     * Fetch lyrics for a song
     * @return Lyrics text or null if not found
     */
    suspend fun getLyrics(title: String, artist: String, duration: Long? = null): Result<LyricsResult> {
        return withContext(Dispatchers.IO) {
            try {
                // Build URL with query params
                val urlBuilder = StringBuilder(LRCLIB_API)
                    .append("?track_name=").append(java.net.URLEncoder.encode(title, "UTF-8"))
                    .append("&artist_name=").append(java.net.URLEncoder.encode(artist, "UTF-8"))
                
                duration?.let {
                    val durationSeconds = it / 1000
                    urlBuilder.append("&duration=").append(durationSeconds)
                }
                
                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("User-Agent", "AudioFlow/1.0")
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Lyrics not found"))
                }
                
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = JSONObject(body)
                
                val plainLyrics = json.optString("plainLyrics", null)
                val syncedLyrics = json.optString("syncedLyrics", null)
                
                if (plainLyrics.isNullOrBlank() && syncedLyrics.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No lyrics available"))
                }
                
                // Parse synced lyrics if available
                val syncedLines = syncedLyrics?.let { parseSyncedLyrics(it) }
                
                Result.success(LyricsResult(
                    plainText = plainLyrics ?: syncedLyrics?.replace(Regex("\\[\\d+:\\d+\\.\\d+\\]"), "")?.trim() ?: "",
                    syncedLines = syncedLines
                ))
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Parse LRC format synced lyrics
     * Format: [mm:ss.xx] line text
     */
    private fun parseSyncedLyrics(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val pattern = Regex("\\[(\\d+):(\\d+\\.\\d+)\\](.+)")
        
        lrc.lines().forEach { line ->
            val match = pattern.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0
                val seconds = match.groupValues[2].toDoubleOrNull() ?: 0.0
                val text = match.groupValues[3].trim()
                
                val timestampMs = (minutes * 60 * 1000) + (seconds * 1000).toLong()
                lines.add(LyricLine(timestampMs, text))
            }
        }
        
        return lines.sortedBy { it.timestampMs }
    }
}

/**
 * Result containing lyrics data
 */
data class LyricsResult(
    val plainText: String,
    val syncedLines: List<LyricLine>? = null
) {
    /**
     * Get preview text (first few lines)
     */
    fun getPreview(lineCount: Int = 5): String {
        return plainText.lines()
            .filter { it.isNotBlank() }
            .take(lineCount)
            .joinToString("\n")
    }
}

/**
 * Single line of synced lyrics with timestamp
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)
