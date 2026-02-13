package com.audioflow.player.data.remote

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Enhanced lyrics provider with multi-strategy search and caching.
 *
 * Supports all languages natively — LRCLIB stores lyrics in their original script
 * (Devanagari for Hindi, Malayalam script, Tamil, etc.).
 *
 * Search strategy (3-step fallback):
 * 1. Exact match via /api/get (fastest, most accurate)
 * 2. Fuzzy search via /api/search?q=title+artist (catches naming variations)
 * 3. Title-only search via /api/search?q=title (catches romanized/translated titles)
 *
 * Features:
 * - In-memory LRU cache (50 songs) for instant revisits
 * - Enhanced YouTube title cleaning for Indian music patterns
 * - Duration-based best-match selection from search results
 */
@Singleton
class LyricsProvider @Inject constructor() {

    companion object {
        private const val TAG = "LyricsProvider"
        private const val LRCLIB_API_GET = "https://lrclib.net/api/get"
        private const val LRCLIB_API_SEARCH = "https://lrclib.net/api/search"
        private const val CACHE_SIZE = 50
        private const val DURATION_TOLERANCE_SEC = 15 // Accept results within ±15s of actual duration
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // LRU cache: trackId -> LyricsResult (survives app session)
    private val lyricsCache = LruCache<String, LyricsResult>(CACHE_SIZE)

    /**
     * Fetch lyrics for a song, using cache and multi-strategy fallback.
     *
     * @param title Song title (may contain YouTube suffixes)
     * @param artist Artist name (may contain "- Topic", "VEVO", etc.)
     * @param duration Track duration in milliseconds
     * @param trackId Unique track identifier for caching
     * @return Lyrics result or failure
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Long? = null,
        trackId: String? = null
    ): Result<LyricsResult> {
        // Check cache first (instant return)
        val cacheKey = trackId ?: "$title|$artist"
        lyricsCache.get(cacheKey)?.let { cached ->
            Log.d(TAG, "Cache hit for: $cacheKey")
            return Result.success(cached)
        }

        return withContext(Dispatchers.IO) {
            val cleanTitle = cleanSongTitle(title)
            val cleanArtist = cleanArtistName(artist)
            val durationSec = duration?.let { it / 1000 }

            Log.d(TAG, "Fetching lyrics: '$cleanTitle' by '$cleanArtist' (${durationSec}s)")

            // Strategy 1: Exact match via /api/get
            var result = fetchExactMatch(cleanTitle, cleanArtist, durationSec)

            // Strategy 1b: Exact match without duration (more lenient)
            if (result.isFailure && durationSec != null) {
                result = fetchExactMatch(cleanTitle, cleanArtist, null)
            }

            // Strategy 1c: Exact match with original title
            if (result.isFailure) {
                result = fetchExactMatch(title, artist, null)
            }

            // Strategy 2: Fuzzy search with title + artist
            if (result.isFailure) {
                Log.d(TAG, "Exact match failed, trying search: '$cleanTitle $cleanArtist'")
                result = fetchSearchMatch("$cleanTitle $cleanArtist", durationSec)
            }

            // Strategy 3: Title-only search (catches romanized/transliterated titles)
            if (result.isFailure) {
                Log.d(TAG, "Title+artist search failed, trying title-only: '$cleanTitle'")
                result = fetchSearchMatch(cleanTitle, durationSec)
            }

            // Strategy 4: Try with original (uncleaned) title as search
            if (result.isFailure && title != cleanTitle) {
                Log.d(TAG, "Clean title search failed, trying original: '$title'")
                result = fetchSearchMatch(title, durationSec)
            }

            // Cache successful result
            result.onSuccess { lyricsResult ->
                lyricsCache.put(cacheKey, lyricsResult)
                Log.d(TAG, "Lyrics found and cached for: $cacheKey")
            }

            result
        }
    }

    /**
     * Pre-cache lyrics for a track (called for adjacent queue items).
     * Runs silently — failures are ignored.
     */
    suspend fun preFetchLyrics(
        title: String,
        artist: String,
        duration: Long? = null,
        trackId: String? = null
    ) {
        val cacheKey = trackId ?: "$title|$artist"
        if (lyricsCache.get(cacheKey) != null) return // Already cached

        try {
            getLyrics(title, artist, duration, trackId)
        } catch (e: Exception) {
            // Silent failure for pre-fetch
            Log.d(TAG, "Pre-fetch failed for '$title': ${e.message}")
        }
    }

    /**
     * Check if lyrics are already cached for a given track.
     */
    fun isCached(trackId: String): Boolean = lyricsCache.get(trackId) != null

    /**
     * Get cached lyrics without network call.
     */
    fun getCached(trackId: String): LyricsResult? = lyricsCache.get(trackId)

    // ============================================================
    // FETCH STRATEGIES
    // ============================================================

    /**
     * Strategy 1: Exact match via /api/get
     */
    private fun fetchExactMatch(title: String, artist: String, durationSec: Long?): Result<LyricsResult> {
        return try {
            val urlBuilder = StringBuilder(LRCLIB_API_GET)
                .append("?track_name=").append(java.net.URLEncoder.encode(title, "UTF-8"))
                .append("&artist_name=").append(java.net.URLEncoder.encode(artist, "UTF-8"))

            durationSec?.let { urlBuilder.append("&duration=").append(it) }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "AudioFlow/1.0 (https://github.com/MrCode-2005/AudioFlow)")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Exact match: HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = JSONObject(body)

            parseLyricsFromJson(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Strategy 2/3: Fuzzy search via /api/search, pick best result by duration match.
     */
    private fun fetchSearchMatch(query: String, durationSec: Long?): Result<LyricsResult> {
        return try {
            val url = "$LRCLIB_API_SEARCH?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AudioFlow/1.0 (https://github.com/MrCode-2005/AudioFlow)")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(Exception("Search: HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val jsonArray = JSONArray(body)

            if (jsonArray.length() == 0) {
                return Result.failure(Exception("No search results"))
            }

            // Find best match: prefer synced lyrics + closest duration
            val bestMatch = selectBestResult(jsonArray, durationSec)
                ?: return Result.failure(Exception("No suitable result found"))

            parseLyricsFromJson(bestMatch)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // RESULT SELECTION & PARSING
    // ============================================================

    /**
     * Select the best result from search results based on:
     * 1. Has non-empty lyrics (plain or synced)
     * 2. Not instrumental
     * 3. Has synced lyrics (preferred)
     * 4. Closest duration match
     */
    private fun selectBestResult(results: JSONArray, targetDurationSec: Long?): JSONObject? {
        var bestResult: JSONObject? = null
        var bestScore = -1

        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)

            // Skip instrumental tracks
            if (item.optBoolean("instrumental", false)) continue

            val plainLyrics = item.optString("plainLyrics", "")
            val syncedLyrics = item.optString("syncedLyrics", "")

            // Must have some lyrics
            if (plainLyrics.isBlank() && syncedLyrics.isBlank()) continue

            var score = 0

            // Prefer synced lyrics (+10 points)
            if (syncedLyrics.isNotBlank()) score += 10

            // Duration match (+5 points if within tolerance, 0 otherwise)
            if (targetDurationSec != null) {
                val itemDuration = item.optDouble("duration", 0.0).toLong()
                val diff = abs(itemDuration - targetDurationSec)
                if (diff <= DURATION_TOLERANCE_SEC) {
                    score += 5
                    // Bonus for very close match
                    if (diff <= 3) score += 3
                }
            }

            // Prefer plain lyrics that are longer (+1 point per 100 chars, max 5)
            score += (plainLyrics.length / 100).coerceAtMost(5)

            if (score > bestScore) {
                bestScore = score
                bestResult = item
            }
        }

        return bestResult
    }

    /**
     * Parse a single LRCLIB JSON object into a LyricsResult.
     */
    private fun parseLyricsFromJson(json: JSONObject): Result<LyricsResult> {
        val plainLyrics = json.optString("plainLyrics", null)
        val syncedLyrics = json.optString("syncedLyrics", null)

        if (plainLyrics.isNullOrBlank() && syncedLyrics.isNullOrBlank()) {
            return Result.failure(Exception("No lyrics available"))
        }

        val syncedLines = syncedLyrics?.let { parseSyncedLyrics(it) }

        return Result.success(LyricsResult(
            plainText = plainLyrics
                ?: syncedLyrics?.replace(Regex("\\[\\d+:\\d+\\.\\d+\\]"), "")?.trim()
                ?: "",
            syncedLines = syncedLines,
            source = "lrclib"
        ))
    }

    // ============================================================
    // LRC PARSING
    // ============================================================

    /**
     * Parse LRC format synced lyrics.
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

    // ============================================================
    // TITLE / ARTIST CLEANING
    // ============================================================

    /**
     * Clean song title by removing common YouTube video suffixes and Indian music patterns.
     */
    private fun cleanSongTitle(title: String): String {
        return title
            // Standard YouTube suffixes
            .replace(Regex("\\s*\\(Official\\s*(Video|Audio|Music Video|Lyric Video|Visualizer|Lyrics?)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Official\\s*(Video|Audio|Music Video|Lyric Video|Visualizer|Lyrics?)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*Official\\s*(Video|Audio|Music Video)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\|\\s*Official\\s*(Video|Audio|Music Video)\\s*", RegexOption.IGNORE_CASE), "")
            // Indian music patterns
            .replace(Regex("\\s*\\(From\\s*\"[^\"]+\"\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(From\\s*'[^']+'\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*Film Version\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Original Motion Picture Soundtrack\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Soundtrack\\)\\s*", RegexOption.IGNORE_CASE), "")
            // Audio quality tags
            .replace(Regex("\\s*\\[(HD|HQ|4K|8K|1080p|720p)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\((HD|HQ|4K|8K|1080p|720p)\\)\\s*", RegexOption.IGNORE_CASE), "")
            // Featuring patterns
            .replace(Regex("\\s*ft\\.?\\s*", RegexOption.IGNORE_CASE), " feat. ")
            .replace(Regex("\\s*feat\\.?\\s*", RegexOption.IGNORE_CASE), " feat. ")
            // Video type tags
            .replace(Regex("\\s*\\(Lyrical\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Audio\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Full Song\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Full Video\\)\\s*", RegexOption.IGNORE_CASE), "")
            // Pipes and dashes at end
            .replace(Regex("\\s*[|].*$"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Clean artist name by removing YouTube channel suffixes.
     */
    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*VEVO$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Official$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Music$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/**
 * Result containing lyrics data.
 */
data class LyricsResult(
    val plainText: String,
    val syncedLines: List<LyricLine>? = null,
    val source: String = "lrclib"
) {
    /**
     * Get preview text (first few lines).
     */
    fun getPreview(lineCount: Int = 5): String {
        return plainText.lines()
            .filter { it.isNotBlank() }
            .take(lineCount)
            .joinToString("\n")
    }
}

/**
 * Single line of synced lyrics with timestamp.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)
