package com.audioflow.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.remote.YouTubeSearchResult
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

/**
 * Search mode options
 */
enum class SearchMode {
    LOCAL,      // Search local device music
    YOUTUBE     // Search YouTube for streaming
}

data class SearchUiState(
    val query: String = "",
    val searchMode: SearchMode = SearchMode.LOCAL,
    // Local results
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    // YouTube results
    val youtubeResults: List<YouTubeSearchResult> = emptyList(),
    val youtubeMetadata: YouTubeMetadata? = null,
    // Loading states
    val isSearching: Boolean = false,
    val isYouTubeLoading: Boolean = false,
    val isExtractingStream: Boolean = false,
    // Errors
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
    
    /**
     * Toggle between local and YouTube search modes
     */
    fun toggleSearchMode() {
        val newMode = when (_uiState.value.searchMode) {
            SearchMode.LOCAL -> SearchMode.YOUTUBE
            SearchMode.YOUTUBE -> SearchMode.LOCAL
        }
        _uiState.value = _uiState.value.copy(
            searchMode = newMode,
            // Clear previous results when switching modes
            tracks = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
            youtubeResults = emptyList(),
            youtubeMetadata = null,
            youtubeError = null,
            hasResults = false
        )
        // Re-search with new mode if there's a query
        if (_uiState.value.query.isNotBlank()) {
            performSearch(_uiState.value.query)
        }
    }
    
    /**
     * Set search mode directly
     */
    fun setSearchMode(mode: SearchMode) {
        if (_uiState.value.searchMode != mode) {
            _uiState.value = _uiState.value.copy(
                searchMode = mode,
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                youtubeResults = emptyList(),
                youtubeMetadata = null,
                youtubeError = null,
                hasResults = false
            )
            if (_uiState.value.query.isNotBlank()) {
                performSearch(_uiState.value.query)
            }
        }
    }
    
    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                youtubeResults = emptyList(),
                youtubeMetadata = null,
                youtubeError = null,
                isSearching = false,
                hasResults = false
            )
            return
        }
        
        _uiState.value = _uiState.value.copy(isSearching = true, youtubeError = null)
        
        // Check if it's a YouTube URL (handle regardless of mode)
        if (mediaRepository.isValidYouTubeUrl(query)) {
            fetchYouTubeMetadata(query)
            return
        }
        
        when (_uiState.value.searchMode) {
            SearchMode.LOCAL -> performLocalSearch(query)
            SearchMode.YOUTUBE -> performYouTubeSearch(query)
        }
    }
    
    private fun performLocalSearch(query: String) {
        val tracks = mediaRepository.searchTracks(query)
        val albums = mediaRepository.searchAlbums(query)
        val artists = mediaRepository.searchArtists(query)
        
        _uiState.value = _uiState.value.copy(
            tracks = tracks,
            albums = albums,
            artists = artists,
            youtubeResults = emptyList(),
            youtubeMetadata = null,
            isSearching = false,
            hasResults = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
        )
    }
    
    private fun performYouTubeSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isYouTubeLoading = true)
            
            mediaRepository.searchYouTube(query)
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(
                        youtubeResults = results,
                        tracks = emptyList(),
                        albums = emptyList(),
                        artists = emptyList(),
                        youtubeMetadata = null,
                        isSearching = false,
                        isYouTubeLoading = false,
                        hasResults = results.isNotEmpty()
                    )
                }
                .onFailure { error ->
                val friendlyMessage = when {
                    error.message?.contains("ServiceUnavailable") == true -> 
                        "YouTube streaming is temporarily unavailable. Please try again later."
                    error.message?.contains("No") == true && error.message?.contains("available") == true ->
                        "YouTube servers are busy. Please try again in a few minutes."
                    else -> error.message ?: "Failed to search YouTube"
                }
                _uiState.value = _uiState.value.copy(
                    youtubeResults = emptyList(),
                    youtubeError = friendlyMessage,
                    isSearching = false,
                    isYouTubeLoading = false,
                    hasResults = false
                )
            }
        }
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
    
    /**
     * Play a YouTube search result by extracting its stream URL
     */
    fun playYouTubeResult(result: YouTubeSearchResult) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtractingStream = true)
            
            mediaRepository.getYouTubeStreamUrl(result.videoId)
                .onSuccess { streamInfo ->
                    val track = mediaRepository.createTrackFromYouTube(streamInfo)
                    playerController.play(track)
                    _uiState.value = _uiState.value.copy(isExtractingStream = false)
                }
                .onFailure { error ->
                val friendlyMessage = when {
                    error.message?.contains("ServiceUnavailable") == true -> 
                        "Stream temporarily unavailable. Please try another song or retry later."
                    error.message?.contains("extraction") == true ||
                    error.message?.contains("failed") == true ->
                        "Unable to play this song. The streaming service may be down."
                    else -> "Failed to play: ${error.message}"
                }
                _uiState.value = _uiState.value.copy(
                    isExtractingStream = false,
                    youtubeError = friendlyMessage
                )
            }
        }
    }
    
    fun clearSearch() {
        _uiState.value = SearchUiState(searchMode = _uiState.value.searchMode)
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
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(youtubeError = null)
    }
}
