package com.audioflow.player.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.local.Playlist
import com.audioflow.player.data.local.PlaylistManager
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.model.Track
import com.audioflow.player.model.TrackSource
import com.audioflow.player.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistManager: PlaylistManager,
    private val mediaRepository: MediaRepository,
    private val playerController: PlayerController,
    private val downloadRepository: com.audioflow.player.data.repository.DownloadRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val playbackState = playerController.playbackState

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])
    
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()
    
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    
    init {
        loadPlaylist()
    }
    
    private fun loadPlaylist() {
        viewModelScope.launch {
            val p = playlistManager.getPlaylist(playlistId)
            _playlist.value = p
            
            p?.let { playlist ->
                val loadedTracks = playlist.trackIds.mapNotNull { trackId ->
                    mediaRepository.getTrackById(trackId)
                }
                _tracks.value = loadedTracks
            }
        }
    }
    
    fun playTrack(track: Track) {
        val currentTracks = _tracks.value
        val index = currentTracks.indexOfFirst { it.id == track.id }
        
        if (index != -1) {
            // Use playPlaylist to enable lazy loading for YouTube tracks
            playerController.playPlaylist(currentTracks, index)
        }
    }
    
    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            playlistManager.removeFromPlaylist(playlistId, track.id)
            loadPlaylist() // Reload to refresh list
        }
    }

    fun playAll() {
        if (_tracks.value.isNotEmpty()) {
            playerController.playPlaylist(_tracks.value, 0)
        }
    }

    fun shufflePlay() {
        if (_tracks.value.isNotEmpty()) {
            playerController.toggleShuffle()
            // If not playing from this playlist, start playing
            // Logic handled by playerController mostly, but if stopped, start random
            playerController.playPlaylist(_tracks.value, (0 until _tracks.value.size).random())
        }
    }

    fun downloadAll() {
        viewModelScope.launch {
            _tracks.value.forEach { track ->
                downloadRepository.startDownload(track)
            }
        }
    }
    
    // Move a track up in the playlist
    fun moveTrackUp(track: Track) {
        val currentTracks = _tracks.value
        val index = currentTracks.indexOfFirst { it.id == track.id }
        if (index > 0) {
            playlistManager.reorderTrackInPlaylist(playlistId, index, index - 1)
            loadPlaylist()
        }
    }
    
    // Move a track down in the playlist
    fun moveTrackDown(track: Track) {
        val currentTracks = _tracks.value
        val index = currentTracks.indexOfFirst { it.id == track.id }
        if (index >= 0 && index < currentTracks.size - 1) {
            playlistManager.reorderTrackInPlaylist(playlistId, index, index + 1)
            loadPlaylist()
        }
    }
    
    // Download a single track
    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            downloadRepository.startDownload(track)
        }
    }
    
    // Save track to device's public "Songs \uD83C\uDFB6" folder
    fun saveTrackToDevice(track: Track) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = downloadRepository.saveToDevice(track)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (result.isSuccess) {
                    android.widget.Toast.makeText(
                        appContext,
                        "Saved to Songs \uD83C\uDFB6 folder",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        appContext,
                        "Save failed: ${result.exceptionOrNull()?.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    // Rename the current playlist
    fun renamePlaylist(newName: String) {
        if (newName.isNotBlank()) {
            playlistManager.renamePlaylist(playlistId, newName)
            loadPlaylist()
        }
    }
    
    // Delete the current playlist
    fun deletePlaylist() {
        playlistManager.deletePlaylist(playlistId)
    }
    
    fun togglePlayPause() {
        playerController.togglePlayPause()
    }
    
    fun playNext() {
        playerController.next()
    }
    
    fun playPrevious() {
        playerController.previous()
    }
    
    // Add a track to this playlist
    fun addTrack(track: Track) {
        viewModelScope.launch {
            playlistManager.addToPlaylist(playlistId, track)
            loadPlaylist()
        }
    }
}
