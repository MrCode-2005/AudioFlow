package com.audioflow.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.model.Album
import com.audioflow.player.model.Artist
import com.audioflow.player.model.Playlist
import com.audioflow.player.model.Track
import com.audioflow.player.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class LibraryFilter {
    PLAYLISTS, ARTISTS, ALBUMS, SONGS
}

data class LibraryUiState(
    val selectedFilter: LibraryFilter = LibraryFilter.SONGS,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playerController: PlayerController
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    val playbackState = playerController.playbackState
    
    init {
        loadLibrary()
    }
    
    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            mediaRepository.refreshLocalMusic()
            
            _uiState.value = _uiState.value.copy(
                tracks = mediaRepository.getAllTracks(),
                albums = mediaRepository.getAllAlbums(),
                artists = mediaRepository.getAllArtists(),
                isLoading = false
            )
        }
    }
    
    fun selectFilter(filter: LibraryFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }
    
    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val index = tracks.indexOf(track)
        if (index >= 0) {
            playerController.setQueue(tracks, index)
        } else {
            playerController.play(track)
        }
    }
    
    fun togglePlayPause() {
        playerController.togglePlayPause()
    }
    
    fun playNext() {
        playerController.next()
    }
    
    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            val success = mediaRepository.deleteTrack(track.id)
            if (success) {
                // Refresh the library after deletion
                loadLibrary()
            }
        }
    }
    
    // Playlist Management
    fun createPlaylist(name: String) {
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "",
            tracks = emptyList()
        )
        _uiState.value = _uiState.value.copy(
            playlists = _uiState.value.playlists + newPlaylist
        )
    }
    
    fun deletePlaylist(playlist: Playlist) {
        _uiState.value = _uiState.value.copy(
            playlists = _uiState.value.playlists.filter { it.id != playlist.id }
        )
    }
    
    fun addTrackToPlaylist(track: Track, playlist: Playlist) {
        val updatedPlaylist = playlist.copy(
            tracks = playlist.tracks + track
        )
        _uiState.value = _uiState.value.copy(
            playlists = _uiState.value.playlists.map { 
                if (it.id == playlist.id) updatedPlaylist else it 
            }
        )
    }
    
    fun removeTrackFromPlaylist(track: Track, playlist: Playlist) {
        val updatedPlaylist = playlist.copy(
            tracks = playlist.tracks.filter { it.id != track.id }
        )
        _uiState.value = _uiState.value.copy(
            playlists = _uiState.value.playlists.map { 
                if (it.id == playlist.id) updatedPlaylist else it 
            }
        )
    }
}
