package com.audioflow.player.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.audioflow.player.model.Track
import com.audioflow.player.model.TrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages metadata for remote tracks (YouTube) that aren't in the local media store.
 * Allows playlists to store just IDs but retrieve full track info.
 */
@Singleton
class TrackMetadataManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "track_metadata"
        private const val KEY_TRACKS = "tracks"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // In-memory cache of tracks: Map<TrackId, Track>
    private val _tracks = MutableStateFlow<Map<String, Track>>(loadTracks())
    val tracks: StateFlow<Map<String, Track>> = _tracks.asStateFlow()
    
    private fun loadTracks(): Map<String, Track> {
        val jsonString = prefs.getString(KEY_TRACKS, null) ?: return emptyMap()
        val map = mutableMapOf<String, Track>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                map[id] = Track(
                    id = id,
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.optString("album", "YouTube"),
                    duration = obj.optLong("duration", 0L),
                    artworkUri = obj.optString("artworkUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                    contentUri = Uri.parse(obj.getString("contentUri")),
                    source = TrackSource.valueOf(obj.optString("source", "YOUTUBE")),
                    dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }
    
    private fun saveTracks(map: Map<String, Track>) {
        val jsonArray = JSONArray()
        map.values.forEach { track ->
            val obj = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("duration", track.duration)
                put("artworkUri", track.artworkUri?.toString() ?: "")
                put("contentUri", track.contentUri.toString())
                put("source", track.source.name)
                put("dateAdded", track.dateAdded)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_TRACKS, jsonArray.toString()).apply()
        _tracks.value = map
    }
    
    /**
     * Get a track by ID
     */
    fun getTrack(id: String): Track? {
        return _tracks.value[id]
    }
    
    /**
     * Save metadata for a track
     */
    fun saveTrack(track: Track) {
        val current = _tracks.value.toMutableMap()
        current[track.id] = track
        saveTracks(current)
    }
    
    /**
     * Save metadata for multiple tracks
     */
    fun saveTracks(tracks: List<Track>) {
        val current = _tracks.value.toMutableMap()
        tracks.forEach { track ->
            current[track.id] = track
        }
        saveTracks(current)
    }
}
