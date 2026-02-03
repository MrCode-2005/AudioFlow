package com.audioflow.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.model.Album
import com.audioflow.player.model.Artist
import com.audioflow.player.model.Track
import com.audioflow.player.model.YouTubeMetadata
import com.audioflow.player.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val youtubeMetadata: YouTubeMetadata? = null,
    val isSearching: Boolean = false,
    val isYouTubeLoading: Boolean = false,
    val youtubeError: String? = null,
    val hasResults: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playerController: PlayerController
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    val playbackState = playerController.playbackState
    
    private var searchJob: Job? = null
    
    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        
        // Debounce search
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch(query)
        }
    }
    
    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }
        
        _uiState.value = _uiState.value.copy(isSearching = true)
        
        // Check if it's a YouTube URL
        if (mediaRepository.isValidYouTubeUrl(query)) {
            fetchYouTubeMetadata(query)
            return
        }
        
        // Search local music
        val tracks = mediaRepository.searchTracks(query)
        val albums = mediaRepository.searchAlbums(query)
        val artists = mediaRepository.searchArtists(query)
        
        _uiState.value = _uiState.value.copy(
            tracks = tracks,
            albums = albums,
            artists = artists,
            youtubeMetadata = null,
            isSearching = false,
            hasResults = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
        )
    }
    
    private fun fetchYouTubeMetadata(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isYouTubeLoading = true,
                youtubeError = null
            )
            
            mediaRepository.fetchYouTubeMetadata(url)
                .onSuccess { metadata ->
                    _uiState.value = _uiState.value.copy(
                        youtubeMetadata = metadata,
                        isYouTubeLoading = false,
                        isSearching = false,
                        hasResults = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        youtubeMetadata = null,
                        youtubeError = error.message ?: "Failed to fetch video info",
                        isYouTubeLoading = false,
                        isSearching = false
                    )
                }
        }
    }
    
    fun clearSearch() {
        _uiState.value = SearchUiState()
    }
    
    fun playTrack(track: Track) {
        playerController.play(track)
    }
    
    fun togglePlayPause() {
        playerController.togglePlayPause()
    }
    
    fun playNext() {
        playerController.next()
    }
}
