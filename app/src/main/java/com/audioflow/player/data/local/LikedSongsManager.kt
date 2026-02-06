package com.audioflow.player.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages liked songs using SharedPreferences
 */
@Singleton
class LikedSongsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "liked_songs"
        private const val KEY_LIKED_IDS = "liked_ids"
        private const val SEPARATOR = "|||"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _likedSongIds = MutableStateFlow<Set<String>>(loadLikedSongs())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()
    
    private fun loadLikedSongs(): Set<String> {
        val idsString = prefs.getString(KEY_LIKED_IDS, "") ?: ""
        return if (idsString.isEmpty()) {
            emptySet()
        } else {
            idsString.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
        }
    }
    
    private fun saveLikedSongs(ids: Set<String>) {
        prefs.edit().putString(KEY_LIKED_IDS, ids.joinToString(SEPARATOR)).apply()
        _likedSongIds.value = ids
    }
    
    /**
     * Check if a song is liked
     */
    fun isLiked(trackId: String): Boolean {
        return _likedSongIds.value.contains(trackId)
    }
    
    /**
     * Add a song to liked songs
     */
    fun likeSong(trackId: String) {
        val current = _likedSongIds.value.toMutableSet()
        current.add(trackId)
        saveLikedSongs(current)
    }
    
    /**
     * Remove a song from liked songs
     */
    fun unlikeSong(trackId: String) {
        val current = _likedSongIds.value.toMutableSet()
        current.remove(trackId)
        saveLikedSongs(current)
    }
    
    /**
     * Toggle like status for a song
     * @return true if now liked, false if now unliked
     */
    fun toggleLike(trackId: String): Boolean {
        return if (isLiked(trackId)) {
            unlikeSong(trackId)
            false
        } else {
            likeSong(trackId)
            true
        }
    }
}
