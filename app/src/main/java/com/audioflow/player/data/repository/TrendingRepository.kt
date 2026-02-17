package com.audioflow.player.data.repository

import android.util.Log
import com.audioflow.player.data.remote.TrendingTrack
import com.audioflow.player.data.remote.YouTubeMusicApi
import com.audioflow.player.data.remote.YouTubeSearchResult
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TrendingRepository"

/**
 * Repository for trending/chart music data from YouTube Music.
 *
 * Features:
 * - 1-hour in-memory cache to avoid excessive API calls
 * - Country-specific trending (defaults to India)
 * - Conversion to YouTubeSearchResult for compatibility with existing playback code
 */
@Singleton
class TrendingRepository @Inject constructor(
    private val youtubeMusicApi: YouTubeMusicApi
) {
    // In-memory cache
    private var cachedTracks: List<TrendingTrack> = emptyList()
    private var cacheTimestamp: Long = 0L
    private var cachedCountry: String = ""

    companion object {
        private const val CACHE_DURATION_MS = 60 * 60 * 1000L  // 1 hour
    }

    /**
     * Get trending songs, using cached data if available and fresh.
     *
     * @param countryCode ISO 3166-1 Alpha-2 country code (default: "IN")
     * @return Result containing list of trending tracks
     */
    suspend fun getTrendingSongs(countryCode: String = "IN"): Result<List<TrendingTrack>> {
        // Return cache if fresh and same country
        if (isCacheValid(countryCode)) {
            Log.d(TAG, "Returning ${cachedTracks.size} cached trending tracks")
            return Result.success(cachedTracks)
        }

        // Fetch fresh data
        val result = youtubeMusicApi.getTrendingSongs(countryCode)

        result.onSuccess { tracks ->
            cachedTracks = tracks
            cacheTimestamp = System.currentTimeMillis()
            cachedCountry = countryCode
            Log.d(TAG, "Cached ${tracks.size} trending tracks for $countryCode")
        }

        return result
    }

    /**
     * Force refresh trending data regardless of cache state.
     */
    suspend fun refresh(countryCode: String = "IN"): Result<List<TrendingTrack>> {
        invalidateCache()
        return getTrendingSongs(countryCode)
    }

    /**
     * Clear the trending cache.
     */
    fun invalidateCache() {
        cachedTracks = emptyList()
        cacheTimestamp = 0L
        cachedCountry = ""
    }

    private fun isCacheValid(countryCode: String): Boolean {
        return cachedTracks.isNotEmpty()
            && cachedCountry == countryCode
            && (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    /**
     * Search YouTube Music for songs by query (genres, playlists, etc.)
     */
    suspend fun searchSongs(query: String, maxResults: Int = 15): Result<List<TrendingTrack>> {
        return youtubeMusicApi.searchSongs(query, maxResults)
    }

    /**
     * Search YouTube Music and return results as YouTubeSearchResult for playback compatibility.
     */
    suspend fun searchSongsAsYTResults(query: String, maxResults: Int = 15): Result<List<YouTubeSearchResult>> {
        return searchSongs(query, maxResults).map { tracks ->
            toYouTubeSearchResults(tracks)
        }
    }

    // ==================== CONVERSION UTILITIES ====================

    /**
     * Convert a TrendingTrack to a YouTubeSearchResult for compatibility
     * with the existing playback pipeline (PlayerController, queue, etc.)
     */
    fun toYouTubeSearchResult(track: TrendingTrack): YouTubeSearchResult {
        return YouTubeSearchResult(
            videoId = track.videoId,
            title = track.title,
            artist = track.artist,
            thumbnailUrl = track.thumbnailUrl,
            duration = track.duration
        )
    }

    /**
     * Convert a list of TrendingTracks to YouTubeSearchResults.
     */
    fun toYouTubeSearchResults(tracks: List<TrendingTrack>): List<YouTubeSearchResult> {
        return tracks.map { toYouTubeSearchResult(it) }
    }
}
