package com.audioflow.player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.model.Album
import com.audioflow.player.model.Track
import com.audioflow.player.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentTracks: List<Track> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playerController: PlayerController
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    val playbackState = playerController.playbackState
    
    // Don't auto-load on init - wait for permission and explicit call
    // init {
    //     loadMusic()
    // }
    
    fun loadMusic() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                mediaRepository.refreshLocalMusic()
                
                val allTracks = mediaRepository.getAllTracks()
                val allAlbums = mediaRepository.getAllAlbums()
                
                _uiState.value = HomeUiState(
                    recentTracks = allTracks.sortedByDescending { it.dateAdded }.take(10),
                    recentAlbums = allAlbums.take(6),
                    allTracks = allTracks,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun playTrack(track: Track) {
        playerController.play(track)
    }
    
    fun playAllTracks(startIndex: Int = 0) {
        val tracks = _uiState.value.allTracks
        if (tracks.isNotEmpty()) {
            playerController.setQueue(tracks, startIndex)
        }
    }
    
    fun togglePlayPause() {
        playerController.togglePlayPause()
    }
    
    fun playNext() {
        playerController.next()
    }
}
