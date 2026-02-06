package com.audioflow.player.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RecentlyPlayedManager"

/**
 * Data class representing a recently played song
 */
data class RecentlyPlayedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUri: String?,
    val duration: Long,
    val playedAt: Long // Timestamp when played
)

/**
 * Manages recently played songs history using SharedPreferences with JSON storage
 * Used for both Home screen recents and Search screen history
 */
@Singleton
class RecentlyPlayedManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "recently_played"
        private const val KEY_SONGS = "songs"
        private const val MAX_RECENT_SIZE = 50
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _recentSongs = MutableStateFlow<List<RecentlyPlayedSong>>(emptyList()) // Initialize with empty list
    val recentSongs: StateFlow<List<RecentlyPlayedSong>> = _recentSongs.asStateFlow()
    
    init {
        loadRecentSongs()
    }
    
    private fun loadRecentSongs() {
        try {
            val jsonString = prefs.getString(KEY_SONGS, null)
            if (jsonString != null) {
                val jsonArray = JSONArray(jsonString)
                val songs = mutableListOf<RecentlyPlayedSong>()
                
                for (i in 0 until jsonArray.length()) {
                    try {
                        val obj = jsonArray.getJSONObject(i)
                        songs.add(RecentlyPlayedSong(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            album = obj.getString("album"),
                            thumbnailUri = if (obj.has("thumbnailUri")) obj.getString("thumbnailUri").takeIf { it.isNotEmpty() } else null,
                            duration = obj.getLong("duration"),
                            playedAt = obj.optLong("playedAt", System.currentTimeMillis())
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing recent song at index $i", e)
                    }
                }
                
                // Sort by playedAt descending
                _recentSongs.value = songs.sortedByDescending { it.playedAt }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent songs", e)
            _recentSongs.value = emptyList()
        }
    }
    
    private fun saveRecentSongs(songs: List<RecentlyPlayedSong>) {
        try {
            val jsonArray = JSONArray()
            songs.forEach { song ->
                val obj = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("thumbnailUri", song.thumbnailUri ?: "")
                    put("duration", song.duration)
                    put("playedAt", song.playedAt)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_SONGS, jsonArray.toString()).apply()
            _recentSongs.value = songs
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recent songs", e)
        }
    }
    
    /**
     * Add a song to recently played (moves to top if exists)
     */
    fun addSong(
        id: String,
        title: String,
        artist: String,
        album: String,
        thumbnailUri: Uri?,
        duration: Long
    ) {
        val current = _recentSongs.value.toMutableList()
        // Remove if already exists (to move to top)
        current.removeAll { it.id == id }
        // Add to beginning with current timestamp
        current.add(0, RecentlyPlayedSong(
            id = id,
            title = title,
            artist = artist,
            album = album,
            thumbnailUri = thumbnailUri?.toString(),
            duration = duration,
            playedAt = System.currentTimeMillis()
        ))
        // Limit size
        val trimmed = current.take(MAX_RECENT_SIZE)
        saveRecentSongs(trimmed)
    }
    
    /**
     * Remove a song from recently played
     */
    fun removeSong(id: String) {
        val current = _recentSongs.value.toMutableList()
        current.removeAll { it.id == id }
        saveRecentSongs(current)
    }
    
    /**
     * Clear all recently played songs
     */
    fun clearAll() {
        saveRecentSongs(emptyList())
    }
    
    /**
     * Get recent songs for display (e.g., top 10 for search, top 20 for home)
     */
    fun getRecentSongs(limit: Int = 10): List<RecentlyPlayedSong> {
        return _recentSongs.value.take(limit)
    }
}
