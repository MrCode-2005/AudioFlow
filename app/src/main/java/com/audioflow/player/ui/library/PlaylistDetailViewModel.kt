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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

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
}
