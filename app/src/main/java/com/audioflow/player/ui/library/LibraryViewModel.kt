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
import androidx.lifecycle.SavedStateHandle
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
    private val playerController: PlayerController,
    private val playlistManager: com.audioflow.player.data.local.PlaylistManager,
    private val likedSongsManager: com.audioflow.player.data.local.LikedSongsManager,
    private val downloadRepository: com.audioflow.player.data.repository.DownloadRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    val likedSongs = likedSongsManager.likedSongs
    val downloadedSongs = downloadRepository.allDownloads
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    val playbackState = playerController.playbackState
    
    init {
        loadLibrary()
        
        // Handle deep link / navigation arguments
        savedStateHandle.get<String>("filter")?.let { filterName ->
            try {
                val filter = LibraryFilter.valueOf(filterName)
                selectFilter(filter)
            } catch (e: IllegalArgumentException) {
                // Ignore invalid filter
            }
        }
        
        // Observe playlists from manager
        viewModelScope.launch {
            playlistManager.playlists.collect { localPlaylists ->
                val mappedPlaylists = localPlaylists.map { local ->
                    val tracks = local.trackIds.mapNotNull { id ->
                        mediaRepository.getTrackById(id)
                    }
                    com.audioflow.player.model.Playlist(
                        id = local.id,
                        name = local.name,
                        tracks = tracks,
                        artworkUri = local.thumbnailUri?.let { android.net.Uri.parse(it) },
                        createdAt = local.createdAt
                    )
                }
                _uiState.value = _uiState.value.copy(playlists = mappedPlaylists)
            }
        }
    }
    
    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                mediaRepository.refreshLocalMusic()
                
                // Filter to only show LOCAL tracks (not YouTube)
                val allTracks = mediaRepository.getAllTracks()
                val localTracks = allTracks.filter { it.source == com.audioflow.player.model.TrackSource.LOCAL }
                
                _uiState.value = _uiState.value.copy(
                    tracks = localTracks,
                    albums = mediaRepository.getAllAlbums(),
                    artists = mediaRepository.getAllArtists(),
                    isLoading = false
                )
            } catch (e: Exception) {
                // Handle error gracefully
                _uiState.value = _uiState.value.copy(
                    isLoading = false
                    // We could expose an error state here if needed
                )
                e.printStackTrace()
            }
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
        viewModelScope.launch {
            playlistManager.createPlaylist(name)
        }
    }
    
    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistManager.deletePlaylist(playlist.id)
        }
    }
    
    fun addTrackToPlaylist(track: Track, playlist: Playlist) {
        viewModelScope.launch {
            playlistManager.addToPlaylist(playlist.id, track.id)
        }
    }
    
    fun removeTrackFromPlaylist(track: Track, playlist: Playlist) {
        viewModelScope.launch {
            playlistManager.removeFromPlaylist(playlist.id, track.id)
        }
    }
    
    // Rename a playlist
    fun renamePlaylist(playlist: Playlist, newName: String) {
        viewModelScope.launch {
            playlistManager.renamePlaylist(playlist.id, newName)
        }
    }
    
    // Move playlist up in the list
    fun movePlaylistUp(playlist: Playlist) {
        val currentList = _uiState.value.playlists
        val index = currentList.indexOfFirst { it.id == playlist.id }
        if (index > 0) {
            // Swap in the underlying manager - we need to update order
            playlistManager.reorderPlaylists(index, index - 1)
        }
    }
    
    // Move playlist down in the list
    fun movePlaylistDown(playlist: Playlist) {
        val currentList = _uiState.value.playlists
        val index = currentList.indexOfFirst { it.id == playlist.id }
        if (index >= 0 && index < currentList.size - 1) {
            playlistManager.reorderPlaylists(index, index + 1)
        }
    }
}
