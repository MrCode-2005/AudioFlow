package com.audioflow.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.local.LikedSongsManager
import com.audioflow.player.data.local.PlaylistManager
import com.audioflow.player.data.remote.LyricsProvider
import com.audioflow.player.data.remote.LyricsResult
import com.audioflow.player.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * States for the like button
 */
enum class LikeButtonState {
    NOT_LIKED,      // Shows + icon
    LIKED,          // Shows green checkmark
    SHOW_SHEET      // Opens bottom sheet (transient)
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val likedSongsManager: LikedSongsManager,
    private val playlistManager: PlaylistManager,
    private val lyricsProvider: LyricsProvider,
    private val youTubeExtractor: com.audioflow.player.data.remote.YouTubeExtractor
) : ViewModel() {
    
    val playbackState = playerController.playbackState
    val likedSongIds = likedSongsManager.likedSongIds
    val playlists = playlistManager.playlists
    
    private val _showNewPlaylistDialog = MutableStateFlow(false)
    val showNewPlaylistDialog: StateFlow<Boolean> = _showNewPlaylistDialog.asStateFlow()
    
    private val _showPlaylistSheet = MutableStateFlow(false)
    val showPlaylistSheet: StateFlow<Boolean> = _showPlaylistSheet.asStateFlow()
    
    private val _lyrics = MutableStateFlow<LyricsResult?>(null)
    val lyrics: StateFlow<LyricsResult?> = _lyrics.asStateFlow()
    
    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()
    
    // Video background state
    private val _videoStreamUrl = MutableStateFlow<String?>(null)
    val videoStreamUrl: StateFlow<String?> = _videoStreamUrl.asStateFlow()
    
    private val _isLoadingVideo = MutableStateFlow(false)
    val isLoadingVideo: StateFlow<Boolean> = _isLoadingVideo.asStateFlow()
    
    private var lastFetchedTrackId: String? = null
    
    // Options sheet state
    private val _showOptionsSheet = MutableStateFlow(false)
    val showOptionsSheet: StateFlow<Boolean> = _showOptionsSheet.asStateFlow()
    
    // Lyrics visibility preference (default: on)
    private val _lyricsEnabled = MutableStateFlow(true)
    val lyricsEnabled: StateFlow<Boolean> = _lyricsEnabled.asStateFlow()
    
    init {
        // Auto-fetch lyrics and video when track changes
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null && track.id != lastFetchedTrackId) {
                    lastFetchedTrackId = track.id
                    fetchLyrics(track.title, track.artist ?: "Unknown", track.duration)
                    
                    // Check if this is a YouTube track and fetch video
                    if (track.id.startsWith("yt:") || track.id.length == 11) {
                        val videoId = track.id.removePrefix("yt:")
                        fetchVideoStream(videoId)
                    } else {
                        _videoStreamUrl.value = null
                    }
                }
            }
        }
    }
    
    private fun fetchVideoStream(videoId: String) {
        viewModelScope.launch {
            _isLoadingVideo.value = true
            youTubeExtractor.extractVideoStream(videoId)
                .onSuccess { info ->
                    _videoStreamUrl.value = info.videoStreamUrl
                }
                .onFailure {
                    _videoStreamUrl.value = null
                }
            _isLoadingVideo.value = false
        }
    }

    
    private fun fetchLyrics(title: String, artist: String, duration: Long) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _lyrics.value = null
            
            lyricsProvider.getLyrics(title, artist, duration)
                .onSuccess { result ->
                    _lyrics.value = result
                }
                .onFailure {
                    _lyrics.value = null
                }
            
            _isLoadingLyrics.value = false
        }
    }
    
    /**
     * Get like state for current track
     */
    fun isCurrentTrackLiked(): Boolean {
        val trackId = playbackState.value.currentTrack?.id ?: return false
        return likedSongsManager.isLiked(trackId)
    }
    
    /**
     * Handle like button click (3-state cycle)
     * Click 1: Not liked -> Liked (add to liked songs)
     * Click 2: Liked -> Show sheet
     * Click 3+: Sheet actions handle the rest
     */
    fun onLikeButtonClick() {
        val trackId = playbackState.value.currentTrack?.id ?: return
        
        if (!likedSongsManager.isLiked(trackId)) {
            // First click: Add to liked songs
            likedSongsManager.likeSong(trackId)
        } else {
            // Second click: Show bottom sheet
            _showPlaylistSheet.value = true
        }
    }
    
    fun dismissPlaylistSheet() {
        _showPlaylistSheet.value = false
    }
    
    fun removeFromLikedSongs() {
        val trackId = playbackState.value.currentTrack?.id ?: return
        likedSongsManager.unlikeSong(trackId)
        _showPlaylistSheet.value = false
    }
    
    fun togglePlayPause() {
        playerController.togglePlayPause()
    }
    
    fun next() {
        playerController.next()
    }
    
    fun previous() {
        playerController.previous()
    }
    
    fun seekTo(position: Long) {
        playerController.seekTo(position)
    }
    
    fun seekToProgress(progress: Float) {
        playerController.seekToProgress(progress)
    }
    
    fun toggleShuffle() {
        playerController.toggleShuffle()
    }
    
    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }
    
    // Options sheet management
    fun showOptionsSheet() {
        _showOptionsSheet.value = true
    }
    
    fun dismissOptionsSheet() {
        _showOptionsSheet.value = false
    }
    
    fun toggleLyrics() {
        _lyricsEnabled.value = !_lyricsEnabled.value
    }
    
    // Stub methods for future features
    fun goToArtist() {
        // TODO: Navigate to artist screen
        _showOptionsSheet.value = false
    }
    
    fun goToAlbum() {
        // TODO: Navigate to album screen
        _showOptionsSheet.value = false
    }
    
    fun viewCredits() {
        // TODO: Show credits dialog
        _showOptionsSheet.value = false
    }
    
    fun openSleepTimer() {
        // TODO: Open sleep timer dialog
        _showOptionsSheet.value = false
    }
    
    fun openEqualizer() {
        // TODO: Open equalizer screen
        _showOptionsSheet.value = false
    }
    
    // Playlist management
    fun showCreatePlaylistDialog() {
        _showNewPlaylistDialog.value = true
    }
    
    fun hideCreatePlaylistDialog() {
        _showNewPlaylistDialog.value = false
    }
    
    fun createNewPlaylist(name: String) {
        if (name.isNotBlank()) {
            playlistManager.createPlaylist(name)
            // Optionally add current track to the new playlist
            playbackState.value.currentTrack?.id?.let { trackId ->
                val newPlaylist = playlistManager.playlists.value.lastOrNull()
                newPlaylist?.let {
                    playlistManager.addToPlaylist(it.id, trackId)
                }
            }
        }
        _showNewPlaylistDialog.value = false
        _showPlaylistSheet.value = false
    }
    
    fun addToPlaylist(playlistId: String) {
        playbackState.value.currentTrack?.id?.let { trackId ->
            playlistManager.addToPlaylist(playlistId, trackId)
        }
        _showPlaylistSheet.value = false
    }
    
    fun createNewFolder() {
        playlistManager.createPlaylist("New Folder", isFolder = true)
        _showPlaylistSheet.value = false
    }
}
