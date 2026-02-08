package com.audioflow.player.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a playlist
 */
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackIds: List<String> = emptyList(),
    val thumbnailUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFolder: Boolean = false
)

/**
 * Manager for handling user playlists
 * Uses SharedPreferences for persistence
 */
@Singleton
class PlaylistManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)
    
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    
    init {
        loadPlaylists()
    }
    
    /**
     * Create a new playlist
     */
    fun createPlaylist(name: String, isFolder: Boolean = false): Playlist {
        val playlist = Playlist(
            name = name,
            isFolder = isFolder
        )
        val updatedList = _playlists.value + playlist
        _playlists.value = updatedList
        savePlaylists()
        return playlist
    }
    
    /**
     * Delete a playlist
     */
    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        savePlaylists()
    }
    
    /**
     * Rename a playlist
     */
    fun renamePlaylist(playlistId: String, newName: String) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(name = newName) else it
        }
        savePlaylists()
    }
    
    /**
     * Add a track to a playlist
     */
    fun addToPlaylist(playlistId: String, trackId: String) {
        addTracksToPlaylist(playlistId, listOf(trackId))
    }

    /**
     * Add multiple tracks to a playlist (optimized)
     */
    fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val newIds = trackIds.filter { !playlist.trackIds.contains(it) }
                if (newIds.isNotEmpty()) {
                    playlist.copy(trackIds = playlist.trackIds + newIds)
                } else {
                    playlist
                }
            } else {
                playlist
            }
        }
        savePlaylists()
    }

    /**
     * Remove a track from a playlist
     */
    fun removeFromPlaylist(playlistId: String, trackId: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(trackIds = playlist.trackIds.filter { it != trackId })
            } else {
                playlist
            }
        }
        savePlaylists()
    }
    
    /**
     * Check if track is in playlist
     */
    fun isTrackInPlaylist(playlistId: String, trackId: String): Boolean {
        return _playlists.value.find { it.id == playlistId }?.trackIds?.contains(trackId) ?: false
    }
    
    /**
     * Get a specific playlist
     */
    fun getPlaylist(playlistId: String): Playlist? {
        return _playlists.value.find { it.id == playlistId }
    }
    
    /**
     * Reorder playlists by swapping positions
     */
    fun reorderPlaylists(fromIndex: Int, toIndex: Int) {
        val list = _playlists.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _playlists.value = list
            savePlaylists()
        }
    }
    
    /**
     * Reorder tracks within a playlist
     */
    fun reorderTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val trackList = playlist.trackIds.toMutableList()
                if (fromIndex in trackList.indices && toIndex in trackList.indices) {
                    val item = trackList.removeAt(fromIndex)
                    trackList.add(toIndex, item)
                    playlist.copy(trackIds = trackList)
                } else {
                    playlist
                }
            } else {
                playlist
            }
        }
        savePlaylists()
    }
    
    private fun loadPlaylists() {
        val json = prefs.getString("playlists_json", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<Playlist>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val trackIds = mutableListOf<String>()
                val tracksArray = obj.optJSONArray("trackIds")
                if (tracksArray != null) {
                    for (j in 0 until tracksArray.length()) {
                        trackIds.add(tracksArray.getString(j))
                    }
                }
                list.add(
                    Playlist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        trackIds = trackIds,
                        thumbnailUri = obj.optString("thumbnailUri").takeIf { it.isNotEmpty() },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isFolder = obj.optBoolean("isFolder", false)
                    )
                )
            }
            _playlists.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun savePlaylists() {
        val array = JSONArray()
        _playlists.value.forEach { playlist ->
            val obj = JSONObject().apply {
                put("id", playlist.id)
                put("name", playlist.name)
                put("trackIds", JSONArray(playlist.trackIds))
                put("thumbnailUri", playlist.thumbnailUri ?: "")
                put("createdAt", playlist.createdAt)
                put("isFolder", playlist.isFolder)
            }
            array.put(obj)
        }
        prefs.edit { putString("playlists_json", array.toString()) }
    }
}
