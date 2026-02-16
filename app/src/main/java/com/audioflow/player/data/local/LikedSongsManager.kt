package com.audioflow.player.data.local

import com.audioflow.player.data.local.dao.LikedSongDao
import com.audioflow.player.data.local.entity.LikedSongEntity
import com.audioflow.player.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages liked songs using Room Database
 */
@Singleton
class LikedSongsManager @Inject constructor(
    private val likedSongDao: LikedSongDao,
    private val trackMetadataManager: TrackMetadataManager
) {
    private val scope = CoroutineScope(Dispatchers.IO) // Use IO dispatcher for DB ops

    // Expose full list of liked songs
    val likedSongs: Flow<List<LikedSongEntity>> = likedSongDao.getAllLikedSongs()

    // Expose set of IDs for quick lookup (optimized)
    val likedSongIds: StateFlow<Set<String>> = likedSongDao.getAllLikedSongs()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )
    
    /**
     * Check if a song is liked (Synchronous check against cached StateFlow)
     */
    fun isLiked(trackId: String): Boolean {
        return likedSongIds.value.contains(trackId)
    }
    
    /**
     * Add a song to liked songs
     */
    fun likeSong(track: Track) {
        // Save full track metadata for later resolution (needed for playback)
        trackMetadataManager.saveTrack(track)
        scope.launch {
            val entity = LikedSongEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                thumbnailUrl = track.artworkUri.toString(),
                duration = track.duration
            )
            likedSongDao.insert(entity)
        }
    }
    
    /**
     * Remove a song from liked songs
     */
    fun unlikeSong(trackId: String) {
        scope.launch {
            likedSongDao.delete(trackId)
        }
    }
    
    /**
     * Toggle like status for a song
     */
    fun toggleLike(track: Track) {
        if (isLiked(track.id)) {
            unlikeSong(track.id)
        } else {
            likeSong(track)
        }
    }
}



