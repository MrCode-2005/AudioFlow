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
 * Enhanced lyrics provider with aggressive multi-strategy search and caching.
 *
 * Core principle: Given the SAME song played from different YouTube videos,
 * lyrics MUST be found consistently. This is achieved by:
 * 1. Extracting the core song name through multiple cleaning strategies
 * 2. Trying many search variations (title+artist, title-only, core-name, etc.)
 * 3. Caching results for instant revisits
 *
 * Supports all languages natively — LRCLIB returns lyrics in their original script.
 */
@Singleton
class LyricsProvider @Inject constructor() {

    companion object {
        private const val TAG = "LyricsProvider"
        private const val LRCLIB_API_GET = "https://lrclib.net/api/get"
        private const val LRCLIB_API_SEARCH = "https://lrclib.net/api/search"
        private const val CACHE_SIZE = 50
        private const val DURATION_TOLERANCE_SEC = 30 // Wider tolerance for video vs audio versions
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // LRU cache: cacheKey -> LyricsResult
    private val lyricsCache = LruCache<String, LyricsResult>(CACHE_SIZE)

    /**
     * Fetch lyrics using aggressive multi-strategy search.
     * Tries up to 8+ different search strategies to ensure consistency.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Long? = null,
        trackId: String? = null
    ): Result<LyricsResult> {
        val cacheKey = trackId ?: "$title|$artist"
        lyricsCache.get(cacheKey)?.let { cached ->
            Log.d(TAG, "Cache hit: $cacheKey")
            return Result.success(cached)
        }

        return withContext(Dispatchers.IO) {
            val durationSec = duration?.let { it / 1000 }

            // Generate all possible title/artist variations
            val variations = generateSearchVariations(title, artist)
            Log.d(TAG, "Trying ${variations.size} search variations for: '$title' by '$artist'")

            var result: Result<LyricsResult> = Result.failure(Exception("No strategies tried"))

            for ((variationTitle, variationArtist, strategy) in variations) {
                if (result.isSuccess) break

                Log.d(TAG, "Strategy '$strategy': title='$variationTitle', artist='$variationArtist'")

                when (strategy) {
                    "exact" -> {
                        result = fetchExactMatch(variationTitle, variationArtist, durationSec)
                        if (result.isFailure && durationSec != null) {
                            result = fetchExactMatch(variationTitle, variationArtist, null)
                        }
                    }
                    "search" -> {
                        val query = if (variationArtist.isNotBlank()) {
                            "$variationTitle $variationArtist"
                        } else {
                            variationTitle
                        }
                        result = fetchSearchMatch(query, durationSec)
                    }
                    "search_title_only" -> {
                        result = fetchSearchMatch(variationTitle, durationSec)
                    }
                }
            }

            result.onSuccess { lyricsResult ->
                lyricsCache.put(cacheKey, lyricsResult)
                Log.d(TAG, "Lyrics found and cached: $cacheKey")
            }

            result
        }
    }

    /**
     * Generate all search variations to try, in priority order.
     * This is the KEY to consistent lyrics across different YouTube videos.
     */
    private fun generateSearchVariations(
        rawTitle: String,
        rawArtist: String
    ): List<Triple<String, String, String>> {
        val variations = mutableListOf<Triple<String, String, String>>()
        val cleanTitle = cleanSongTitle(rawTitle)
        val cleanArtist = cleanArtistName(rawArtist)
        val coreName = extractCoreSongName(rawTitle)
        val artistFromTitle = extractArtistFromTitle(rawTitle)

        // 1. Exact match with cleaned title + artist
        variations.add(Triple(cleanTitle, cleanArtist, "exact"))

        // 2. Search with cleaned title + artist
        variations.add(Triple(cleanTitle, cleanArtist, "search"))

        // 3. Search with core song name + artist
        if (coreName != cleanTitle && coreName.isNotBlank()) {
            variations.add(Triple(coreName, cleanArtist, "search"))
        }

        // 4. Search title-only (no artist — catches different channel uploads)
        variations.add(Triple(cleanTitle, "", "search_title_only"))

        // 5. If artist was embedded in title (e.g., "Arijit Singh - Tum Hi Ho"),
        //    search with the extracted parts
        if (artistFromTitle != null) {
            variations.add(Triple(artistFromTitle.second, artistFromTitle.first, "search"))
            variations.add(Triple(artistFromTitle.second, artistFromTitle.first, "exact"))
        }

        // 6. Core song name only search
        if (coreName.isNotBlank() && coreName != cleanTitle) {
            variations.add(Triple(coreName, "", "search_title_only"))
        }

        // 7. Original raw title as search (sometimes YouTube has the most recognizable title)
        if (rawTitle != cleanTitle) {
            variations.add(Triple(rawTitle, "", "search_title_only"))
        }

        // 8. Try without "feat." or "ft." parts
        val titleWithoutFeat = cleanTitle
            .replace(Regex("\\s*(feat\\.?|ft\\.?)\\s+.*$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (titleWithoutFeat != cleanTitle && titleWithoutFeat.isNotBlank()) {
            variations.add(Triple(titleWithoutFeat, cleanArtist, "search"))
        }

        return variations.distinctBy { "${it.first}|${it.second}|${it.third}" }
    }

    /**
     * Extract the absolute core song name by stripping EVERYTHING that isn't
     * likely to be the actual song name. This is aggressive.
     *
     * Examples:
     *  "Arijit Singh - Tum Hi Ho | Aashiqui 2 Full Video" → "Tum Hi Ho"
     *  "Tum Hi Ho (Official Video) | Aashiqui 2" → "Tum Hi Ho"
     *  "KGF Chapter 2 - Toofan Full Song | Yash" → "Toofan"
     *  "Premam | Malare Ninne Video Song" → "Malare Ninne"
     */
    private fun extractCoreSongName(title: String): String {
        var name = title

        // Remove everything after pipes, but try to keep the part that looks like a song name
        val pipeParts = name.split(Regex("\\s*[|]\\s*"))
        if (pipeParts.size > 1) {
            // Pick the shortest non-trivial part that looks like a song name
            // (not "Full Video", "Official Audio", etc.)
            val candidates = pipeParts.filter { part ->
                val lower = part.lowercase().trim()
                lower.length > 2 &&
                !lower.contains("official") &&
                !lower.contains("full video") &&
                !lower.contains("video song") &&
                !lower.contains("lyric") &&
                !lower.contains("audio") &&
                !lower.contains("hd") &&
                !lower.contains("4k") &&
                !lower.contains("movie") &&
                !lower.contains("film") &&
                !lower.contains("soundtrack")
            }
            if (candidates.isNotEmpty()) {
                // Prefer the first candidate (usually the song name)
                name = candidates.first()
            }
        }

        // Handle "Artist - Song Name" or "Song Name - Artist" patterns
        val dashParts = name.split(Regex("\\s*-\\s*"))
        if (dashParts.size == 2) {
            // If one part looks like an artist and the other doesn't, take the song part
            val part1 = dashParts[0].trim()
            val part2 = dashParts[1].trim()
            // Usually "Artist - Song" format. Use the second part.
            // But check if the second part looks like a suffix we should strip
            val part2Lower = part2.lowercase()
            if (part2Lower.contains("official") || part2Lower.contains("full song") ||
                part2Lower.contains("video") || part2Lower.contains("audio")) {
                name = part1
            } else {
                // Could be "Artist - Song" → prefer part2, or "Song - Movie" → prefer part1
                // Heuristic: if part1 is shorter and has fewer words, it's likely the artist
                name = if (part1.split(" ").size <= 2 && part2.split(" ").size >= 2) {
                    part2 // "Arijit Singh - Tum Hi Ho" → "Tum Hi Ho"
                } else {
                    part1 // Keep the first part if unsure
                }
            }
        }

        // Now apply standard cleaning to the extracted name
        name = name
            .replace(Regex("\\s*\\(Official\\s*(Video|Audio|Music Video|Lyric Video|Visualizer|Lyrics?)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Official\\s*(Video|Audio|Music Video|Lyric Video|Visualizer|Lyrics?)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(From\\s*[\"'][^\"']+[\"']\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Lyrical\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Audio\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Full\\s*(Song|Video)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Video\\s*Song\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[(HD|HQ|4K|8K|1080p|720p)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\((HD|HQ|4K|8K|1080p|720p)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Full\\s*Song\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Video\\s*Song\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return name
    }

    /**
     * Try to extract artist name from the title itself.
     * Many YouTube titles embed the artist: "Arijit Singh - Tum Hi Ho"
     * Returns Pair(artist, songTitle) or null.
     */
    private fun extractArtistFromTitle(title: String): Pair<String, String>? {
        // Pattern: "Artist Name - Song Name" or "Artist Name : Song Name"
        val separators = listOf(" - ", " – ", " — ", ": ")
        for (sep in separators) {
            val parts = title.split(sep, limit = 2)
            if (parts.size == 2) {
                val part1 = parts[0].trim()
                val part2 = parts[1].trim()
                // Heuristic: part1 is likely artist if it has 1-3 words
                if (part1.split(" ").size <= 4 && part2.isNotBlank()) {
                    // Clean the song part
                    val songPart = cleanSongTitle(part2)
                    if (songPart.isNotBlank()) {
                        return Pair(part1, songPart)
                    }
                }
            }
        }
        return null
    }

    /**
     * Pre-cache lyrics for a track (called for adjacent queue items).
     */
    suspend fun preFetchLyrics(
        title: String,
        artist: String,
        duration: Long? = null,
        trackId: String? = null
    ) {
        val cacheKey = trackId ?: "$title|$artist"
        if (lyricsCache.get(cacheKey) != null) return
        try {
            getLyrics(title, artist, duration, trackId)
        } catch (e: Exception) {
            Log.d(TAG, "Pre-fetch failed for '$title': ${e.message}")
        }
    }

    fun isCached(trackId: String): Boolean = lyricsCache.get(trackId) != null
    fun getCached(trackId: String): LyricsResult? = lyricsCache.get(trackId)

    // ============================================================
    // FETCH STRATEGIES
    // ============================================================

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
            parseLyricsFromJson(JSONObject(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
     * Android's JSONObject.optString() has a known bug: when the JSON value is null,
     * it returns the literal string "null" instead of the fallback. This helper fixes that.
     */
    private fun JSONObject.safeOptString(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key, "")
        // Guard against the literal "null" string from Android's broken optString
        return if (value == "null" || value.isBlank()) null else value
    }

    private fun selectBestResult(results: JSONArray, targetDurationSec: Long?): JSONObject? {
        var bestResult: JSONObject? = null
        var bestScore = -1

        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            if (item.optBoolean("instrumental", false)) continue

            val plainLyrics = item.safeOptString("plainLyrics") ?: ""
            val syncedLyrics = item.safeOptString("syncedLyrics") ?: ""
            if (plainLyrics.isBlank() && syncedLyrics.isBlank()) continue

            var score = 0

            // Strongly prefer synced lyrics (+15 points)
            if (syncedLyrics.isNotBlank()) score += 15

            // Duration match scoring
            if (targetDurationSec != null) {
                val itemDuration = item.optDouble("duration", 0.0).toLong()
                val diff = abs(itemDuration - targetDurationSec)
                when {
                    diff <= 3 -> score += 10  // Almost exact match
                    diff <= 10 -> score += 7  // Very close
                    diff <= DURATION_TOLERANCE_SEC -> score += 3  // Within tolerance
                }
            }

            // Prefer longer lyrics content (+1 per 100 chars, max 5)
            score += (plainLyrics.length / 100).coerceAtMost(5)

            // Prefer English/Latin script lyrics over non-Latin scripts (+8 points)
            // This ensures English translations are chosen when both exist
            val lyricsToCheck = plainLyrics.ifBlank { syncedLyrics }
            val latinChars = lyricsToCheck.count { it in 'A'..'Z' || it in 'a'..'z' || it == ' ' }
            val totalLetters = lyricsToCheck.count { it.isLetter() }
            if (totalLetters > 0) {
                val latinRatio = latinChars.toFloat() / totalLetters
                if (latinRatio > 0.5f) score += 8  // Primarily Latin/English
            }

            if (score > bestScore) {
                bestScore = score
                bestResult = item
            }
        }

        return bestResult
    }

    private fun parseLyricsFromJson(json: JSONObject): Result<LyricsResult> {
        val plainLyrics = json.safeOptString("plainLyrics")
        val syncedLyrics = json.safeOptString("syncedLyrics")

        if (plainLyrics.isNullOrBlank() && syncedLyrics.isNullOrBlank()) {
            return Result.failure(Exception("No lyrics available"))
        }

        val syncedLines = syncedLyrics?.let { parseSyncedLyrics(it) }
            ?.takeIf { it.isNotEmpty() } // Empty parsed list = no usable synced lyrics

        val plainText = plainLyrics
            ?: syncedLyrics?.replace(Regex("\\[\\d+:\\d+\\.\\d+\\]"), "")?.trim()
            ?: ""

        // Final sanity check: do we actually have displayable content?
        val hasContent = plainText.lines().any { it.isNotBlank() } || syncedLines?.isNotEmpty() == true
        if (!hasContent) {
            return Result.failure(Exception("No displayable lyrics"))
        }

        return Result.success(LyricsResult(
            plainText = plainText,
            syncedLines = syncedLines,
            source = "lrclib"
        ))
    }

    // ============================================================
    // LRC PARSING
    // ============================================================

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
     * Clean song title by removing common YouTube video suffixes.
     * This is a moderate clean — strips obvious noise but preserves structure.
     */
    private fun cleanSongTitle(title: String): String {
        return title
            // Parenthesized tags
            .replace(Regex("\\s*\\(Official\\s*(Video|Audio|Music Video|Lyric Video|Visualizer|Lyrics?|Mv)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Official\\s*(Video|Audio|Music Video|Lyric Video|Visualizer|Lyrics?|Mv)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Lyrical(\\s+Video)?\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Audio\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Full\\s*(Song|Video|Audio)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Video\\s*Song\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Visualizer\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Visualiser\\)\\s*", RegexOption.IGNORE_CASE), "")
            // Indian film patterns
            .replace(Regex("\\s*\\(From\\s*[\"'][^\"']+[\"']\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(From\\s+[^)]+\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Original Motion Picture Soundtrack\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Soundtrack\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*Film Version\\s*", RegexOption.IGNORE_CASE), "")
            // Suffixes after pipes/dashes that are noise
            .replace(Regex("\\s*-\\s*Official\\s*(Video|Audio|Music Video)\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\|\\s*Official\\s*(Video|Audio|Music Video).*$", RegexOption.IGNORE_CASE), "")
            // Quality tags
            .replace(Regex("\\s*[\\[\\(](HD|HQ|4K|8K|1080p|720p)[\\]\\)]\\s*", RegexOption.IGNORE_CASE), "")
            // "ft." → normalize
            .replace(Regex("\\s*\\bft\\.?\\s+", RegexOption.IGNORE_CASE), " feat. ")
            // Trailing pipes with everything after
            .replace(Regex("\\s*\\|.*$"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*VEVO$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Official$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*(Official\\s+)?Music\\s*(Channel)?$", RegexOption.IGNORE_CASE), "")
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
     * Whether this result has any actual displayable lyrics content.
     * Used by the UI to avoid showing an empty lyrics card.
     */
    fun hasDisplayableContent(): Boolean {
        if (syncedLines != null && syncedLines.any { it.text.isNotBlank() }) return true
        return plainText.lines().any { it.isNotBlank() }
    }

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
