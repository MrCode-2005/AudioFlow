package com.audioflow.player.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.local.LikedSongsManager
import com.audioflow.player.data.local.PlaylistManager
import com.audioflow.player.data.remote.LyricsProvider
import com.audioflow.player.data.remote.LyricsResult
import com.audioflow.player.data.remote.YouTubeExtractor
import com.audioflow.player.data.remote.YouTubeVideoStreamInfo
import com.audioflow.player.model.TrackSource
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val playerController: PlayerController,
    private val likedSongsManager: LikedSongsManager,
    private val playlistManager: PlaylistManager,
    private val lyricsProvider: LyricsProvider,
    private val downloadRepository: com.audioflow.player.data.repository.DownloadRepository,
    private val youTubeExtractor: YouTubeExtractor
) : ViewModel() {
    
    companion object {
        private const val TAG = "PlayerViewModel"
        private const val PREFS_NAME = "player_prefs"
        private const val KEY_LYRICS_ENABLED = "lyrics_enabled"
    }
    
    // Preferences
    private val prefs by lazy { 
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) 
    }
    
    val playbackState = playerController.playbackState
    val isBuffering = playerController.isBuffering
    val likedSongIds = likedSongsManager.likedSongIds
    val playlists = playlistManager.playlists
    
    private val _showNewPlaylistDialog = MutableStateFlow(false)
    val showNewPlaylistDialog: StateFlow<Boolean> = _showNewPlaylistDialog.asStateFlow()
    
    private val _showNewFolderDialog = MutableStateFlow(false)
    val showNewFolderDialog: StateFlow<Boolean> = _showNewFolderDialog.asStateFlow()
    
    private val _showPlaylistSheet = MutableStateFlow(false)
    val showPlaylistSheet: StateFlow<Boolean> = _showPlaylistSheet.asStateFlow()
    
    private val _lyrics = MutableStateFlow<LyricsResult?>(null)
    val lyrics: StateFlow<LyricsResult?> = _lyrics.asStateFlow()
    
    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()
    
    private var lastFetchedTrackId: String? = null
    
    // Download status for current track
    private val _downloadStatus = MutableStateFlow(com.audioflow.player.data.local.entity.DownloadStatus.FAILED) // Default to failed/not downloaded
    val downloadStatus: StateFlow<com.audioflow.player.data.local.entity.DownloadStatus> = _downloadStatus.asStateFlow()
    
    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()
    
    // Options sheet state
    private val _showOptionsSheet = MutableStateFlow(false)
    val showOptionsSheet: StateFlow<Boolean> = _showOptionsSheet.asStateFlow()
    
    // Lyrics visibility preference (persisted)
    private val _lyricsEnabled = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_ENABLED, true))
    val lyricsEnabled: StateFlow<Boolean> = _lyricsEnabled.asStateFlow()
    
    // ==================== VIDEO STATE ====================
    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()
    
    private val _videoStreamInfo = MutableStateFlow<YouTubeVideoStreamInfo?>(null)
    val videoStreamInfo: StateFlow<YouTubeVideoStreamInfo?> = _videoStreamInfo.asStateFlow()
    
    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()
    
    // Track which video has been prefetched to avoid redundant calls
    private var videoPrefetchedForTrackId: String? = null
    // ==================================================
    
    // Navigation event for Go to Artist/Album (search query to trigger)
    private val _navigateToSearch = MutableStateFlow<String?>(null)
    val navigateToSearch: StateFlow<String?> = _navigateToSearch.asStateFlow()
    
    fun clearNavigateToSearch() {
        _navigateToSearch.value = null
    }
    
    init {
        // Auto-fetch lyrics when track changes
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    if (track.id != lastFetchedTrackId) {
                        lastFetchedTrackId = track.id
                        fetchLyrics(track.title, track.artist ?: "Unknown", track.duration, track.id)
                        // Pre-fetch lyrics for next 2 songs in queue
                        preFetchAdjacentLyrics(state.queue, state.currentQueueIndex)
                        
                        // Reset video state on track change (music-first)
                        _isVideoMode.value = false
                        _videoStreamInfo.value = null
                        _isVideoLoading.value = false
                        
                        // Lazily prefetch video for current YouTube track
                        if (track.source == TrackSource.YOUTUBE) {
                            prefetchVideoForCurrentTrack(track.id)
                        } else {
                            videoPrefetchedForTrackId = null
                        }
                    }
                    // Check download status
                    checkDownloadStatus(track.id)
                }
            }
        }
    }
    
    // ==================== VIDEO LOGIC ====================
    
    /**
     * Prefetch video stream URL for the CURRENT track only.
     * Runs in background, does NOT block audio or UI.
     * Never prefetches for search results or other queue items.
     */
    private fun prefetchVideoForCurrentTrack(trackId: String) {
        if (videoPrefetchedForTrackId == trackId) return
        videoPrefetchedForTrackId = trackId
        val cleanId = trackId.removePrefix("yt_")
        viewModelScope.launch {
            Log.d(TAG, "Prefetching video for current track: $cleanId")
            youTubeExtractor.extractVideoStream(cleanId)
                .onSuccess { info ->
                    // Only cache if still the same track
                    if (playbackState.value.currentTrack?.id == trackId) {
                        _videoStreamInfo.value = info
                        Log.d(TAG, "Video prefetched for $cleanId (${info.width}x${info.height})")
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "Video prefetch failed for $cleanId: ${e.message}")
                    // Don't crash — music continues
                }
        }
    }
    
    /**
     * Toggle video mode ON/OFF. Only for YouTube tracks.
     * If video is prefetched, shows instantly. Otherwise extracts on demand.
     */
    fun toggleVideoMode() {
        val track = playbackState.value.currentTrack ?: return
        if (track.source != TrackSource.YOUTUBE) return
        
        // If already in video mode, disable it
        if (_isVideoMode.value) {
            _isVideoMode.value = false
            return
        }
        
        // If video is already prefetched, enable instantly
        val cached = _videoStreamInfo.value
        if (cached != null) {
            _isVideoMode.value = true
            return
        }
        
        // Otherwise extract on demand
        _isVideoLoading.value = true
        val cleanId = track.id.removePrefix("yt_")
        viewModelScope.launch {
            youTubeExtractor.extractVideoStream(cleanId)
                .onSuccess { info ->
                    if (playbackState.value.currentTrack?.id == track.id) {
                        _videoStreamInfo.value = info
                        _isVideoMode.value = true
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Video extraction failed: ${e.message}")
                    // Stay in audio mode — no crash
                }
            _isVideoLoading.value = false
        }
    }
    
    /**
     * Check if video is available for the current track (YouTube only).
     */
    fun isVideoAvailable(): Boolean {
        return playbackState.value.currentTrack?.source == TrackSource.YOUTUBE
    }
    
    // ======================================================
    
    private fun checkDownloadStatus(trackId: String) {
        viewModelScope.launch {
            downloadRepository.getDownloadStatus(trackId).collect { status ->
                _downloadStatus.value = status
                _isDownloaded.value = status == com.audioflow.player.data.local.entity.DownloadStatus.COMPLETED
            }
        }
    }

    fun downloadCurrentTrack() {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            val newStatus = downloadRepository.toggleDownload(track)
            _downloadStatus.value = newStatus
            _isDownloaded.value = newStatus == com.audioflow.player.data.local.entity.DownloadStatus.COMPLETED
        }
    }
    
    fun deleteDownload() {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            downloadRepository.deleteDownload(track.id)
            _downloadStatus.value = com.audioflow.player.data.local.entity.DownloadStatus.FAILED
            _isDownloaded.value = false
        }
    }

    
    private fun fetchLyrics(title: String, artist: String, duration: Long, trackId: String) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _lyrics.value = null
            
            lyricsProvider.getLyrics(title, artist, duration, trackId)
                .onSuccess { result ->
                    _lyrics.value = result
                }
            
            _isLoadingLyrics.value = false
        }
    }
    
    /**
     * Pre-fetch lyrics for the next 2 songs in the queue (background, low priority).
     * This ensures lyrics are cached and display instantly when the user swipes.
     */
    private fun preFetchAdjacentLyrics(queue: List<com.audioflow.player.model.Track>, currentIndex: Int) {
        viewModelScope.launch {
            // Pre-fetch next 2 tracks
            for (offset in 1..2) {
                val nextTrack = queue.getOrNull(currentIndex + offset) ?: continue
                lyricsProvider.preFetchLyrics(
                    title = nextTrack.title,
                    artist = nextTrack.artist ?: "Unknown",
                    duration = nextTrack.duration,
                    trackId = nextTrack.id
                )
            }
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
        val track = playbackState.value.currentTrack ?: return
        
        if (!likedSongsManager.isLiked(track.id)) {
            // First click: Add to liked songs
            likedSongsManager.likeSong(track)
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
    
    fun seekToQueueIndex(index: Int) {
        playerController.seekToQueueIndex(index)
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
        val newState = !_lyricsEnabled.value
        _lyricsEnabled.value = newState
        prefs.edit().putBoolean(KEY_LYRICS_ENABLED, newState).apply()
    }
    
    // Go to Artist - triggers search for artist name
    fun goToArtist() {
        playbackState.value.currentTrack?.artist?.let { artist ->
            _navigateToSearch.value = artist
        }
        _showOptionsSheet.value = false
    }
    
    fun goToAlbum() {
        val track = playbackState.value.currentTrack
        if (track != null) {
            // Search for album name, or artist + "album" if album is unknown
            val query = if (track.album.isNotBlank() && track.album != "Unknown") {
                track.album
            } else {
                "${track.artist ?: "Unknown"} album"
            }
            _navigateToSearch.value = query
        }
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
            playbackState.value.currentTrack?.let { track ->
                val newPlaylist = playlistManager.playlists.value.lastOrNull()
                newPlaylist?.let {
                    playlistManager.addToPlaylist(it.id, track)
                }
            }
        }
        _showNewPlaylistDialog.value = false
        _showPlaylistSheet.value = false
    }
    
    fun addToPlaylist(playlistId: String) {
        playbackState.value.currentTrack?.let { track ->
            playlistManager.addToPlaylist(playlistId, track)
        }
        _showPlaylistSheet.value = false
    }
    
    fun createNewFolder() {
        // Show folder name dialog instead of creating with hardcoded name
        _showNewFolderDialog.value = true
    }
    
    fun hideNewFolderDialog() {
        _showNewFolderDialog.value = false
    }
    
    fun confirmNewFolder(name: String) {
        if (name.isNotBlank()) {
            playlistManager.createPlaylist(name, isFolder = true)
            // Add current track to the new folder
            playbackState.value.currentTrack?.let { track ->
                val newFolder = playlistManager.playlists.value.lastOrNull()
                newFolder?.let {
                    playlistManager.addToPlaylist(it.id, track)
                }
            }
        }
        _showNewFolderDialog.value = false
        _showPlaylistSheet.value = false
    }
}
