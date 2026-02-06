package com.audioflow.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.local.RecentlyPlayedManager
import com.audioflow.player.data.local.RecentlyPlayedSong
import com.audioflow.player.data.local.SearchHistoryManager
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
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentUris
import javax.inject.Inject

/**
 * Search mode options
 */
enum class SearchMode {
    LOCAL,      // Search local device music
    YOUTUBE     // Search YouTube for streaming
}

/**
 * Content filter for YouTube results
 */
enum class ContentFilter {
    ALL,        // Show all results
    SONGS,      // Only short videos (< 10 min typically songs)
    PLAYLISTS,  // Filter by playlist-like titles
    PODCASTS    // Long videos (> 20 min)
}

data class SearchUiState(
    val query: String = "",
    val searchMode: SearchMode = SearchMode.YOUTUBE,
    val contentFilter: ContentFilter = ContentFilter.SONGS, // Default to songs
    // Local results
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    // YouTube results
    val youtubeResults: List<YouTubeSearchResult> = emptyList(),
    val filteredResults: List<YouTubeSearchResult> = emptyList(), // Filtered results
    val youtubeMetadata: YouTubeMetadata? = null,
    // Loading states
    val isSearching: Boolean = false,
    val isYouTubeLoading: Boolean = false,
    val isExtractingStream: Boolean = false,
    // Errors
    val youtubeError: String? = null,
    val hasResults: Boolean = false,
    // Keyboard dismiss trigger
    val shouldDismissKeyboard: Boolean = false
)


@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playerController: PlayerController,
    private val searchHistoryManager: SearchHistoryManager,
    private val recentlyPlayedManager: RecentlyPlayedManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    val playbackState = playerController.playbackState
    val searchHistory = searchHistoryManager.history
    val recentlyPlayedSongs = recentlyPlayedManager.recentSongs
    
    private var searchJob: Job? = null
    
    fun updateQuery(query: String) {
        // Clear results immediately when query changes to avoid showing stale results
        _uiState.value = _uiState.value.copy(
            query = query,
            shouldDismissKeyboard = false
        )
        
        // Cancel previous search
        searchJob?.cancel()
        
        if (query.isBlank()) {
            // Clear results when query is empty
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
        
        // Clear YouTube results immediately to prevent showing old results
        if (_uiState.value.searchMode == SearchMode.YOUTUBE) {
            _uiState.value = _uiState.value.copy(
                youtubeResults = emptyList(),
                isSearching = true
            )
        }
        
        // Debounce: 500ms for YouTube (slower API), 300ms for local
        val debounceTime = if (_uiState.value.searchMode == SearchMode.YOUTUBE) 500L else 300L
        searchJob = viewModelScope.launch {
            delay(debounceTime)
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
    
    /**
     * Force an immediate search (bypasses debounce)
     * Use when user explicitly submits via keyboard
     */
    fun forceSearch(query: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = query,
            youtubeResults = emptyList(),
            filteredResults = emptyList(),
            isSearching = true
        )
        viewModelScope.launch {
            performSearch(query)
        }
    }
    
    /**
     * Set content filter and refilter results
     */
    fun setContentFilter(filter: ContentFilter) {
        _uiState.value = _uiState.value.copy(contentFilter = filter)
        applyContentFilter()
    }
    
    /**
     * Apply content filter to current YouTube results
     */
    private fun applyContentFilter() {
        val filter = _uiState.value.contentFilter
        val results = _uiState.value.youtubeResults
        
        // duration is in milliseconds: 10min = 600000ms, 20min = 1200000ms
        val filtered = when (filter) {
            ContentFilter.ALL -> results
            ContentFilter.SONGS -> results.filter { it.duration < 600_000 } // < 10 min
            ContentFilter.PLAYLISTS -> results.filter { 
                it.title.contains("playlist", ignoreCase = true) ||
                it.title.contains("mix", ignoreCase = true) ||
                it.duration > 1_200_000 // > 20 min compilations
            }
            ContentFilter.PODCASTS -> results.filter { it.duration > 1_200_000 } // > 20 min
        }
        
        _uiState.value = _uiState.value.copy(
            filteredResults = filtered,
            hasResults = filtered.isNotEmpty()
        )
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
                    // Save to search history on successful search
                    searchHistoryManager.addSearch(query)
                    
                    _uiState.value = _uiState.value.copy(
                        youtubeResults = results,
                        filteredResults = results, // Set initially, then filter
                        tracks = emptyList(),
                        albums = emptyList(),
                        artists = emptyList(),
                        youtubeMetadata = null,
                        isSearching = false,
                        isYouTubeLoading = false,
                        hasResults = results.isNotEmpty(),
                        shouldDismissKeyboard = true
                    )
                    // Apply filter after setting results
                    applyContentFilter()
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
            
            // Find index of clicked result in filtered results for queue navigation
            val filteredResults = _uiState.value.filteredResults
            val clickedIndex = filteredResults.indexOfFirst { it.videoId == result.videoId }
            
            // Set the YouTube queue for next/previous navigation
            if (clickedIndex >= 0) {
                playerController.setYouTubeQueue(filteredResults, clickedIndex)
            }
            
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
    
    fun onKeyboardDismissed() {
        _uiState.value = _uiState.value.copy(shouldDismissKeyboard = false)
    }
    
    fun clearSearchHistory() {
        searchHistoryManager.clearAll()
    }
    
    fun removeSearchHistoryItem(query: String) {
        searchHistoryManager.removeItem(query)
    }
    
    fun searchFromHistory(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        performSearch(query)
    }
    
    fun playTrack(track: Track) {
        // Clear YouTube queue when playing local tracks
        playerController.clearYouTubeQueue()
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
    
    fun removeRecentlyPlayedSong(songId: String) {
        recentlyPlayedManager.removeSong(songId)
    }
    
    fun playRecentlyPlayedSong(song: RecentlyPlayedSong) {
        val isYouTube = song.id.startsWith("yt_") || song.thumbnailUri?.contains("ytimg") == true
        
        val source = if (isYouTube) com.audioflow.player.model.TrackSource.YOUTUBE else com.audioflow.player.model.TrackSource.LOCAL
        
        val contentUri = if (isYouTube) {
             // For YouTube, we don't have the original stream URL, so we create a dummy one or use ID
             // The player logic below handles extraction if it looks like a YouTube ID
             Uri.parse("https://youtube.com/watch?v=${song.id.removePrefix("yt_")}")
        } else {
             // For local, reconstruct the content URI
             ContentUris.withAppendedId(
                 MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                 song.id.toLongOrNull() ?: -1L
             )
        }

        val track = Track(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            artworkUri = song.thumbnailUri?.let { Uri.parse(it) },
            contentUri = contentUri,
            source = source
        )
        
        // Use existing play logic which handles queue management
        if (track.source == com.audioflow.player.model.TrackSource.YOUTUBE) {
            viewModelScope.launch {
                 // Trigger extraction since the URL we constructed above is likely not a direct stream
                 mediaRepository.getYouTubeStreamUrl(track.id.removePrefix("yt_"))
                    .onSuccess { streamInfo ->
                        val playableTrack = mediaRepository.createTrackFromYouTube(streamInfo)
                        playSound(playableTrack)
                    }
                    .onFailure {
                        // Handle error or try playing anyway if possible
                    }
            }
        } else {
            playSound(track)
        }
    }

    private fun playSound(track: Track) {
        playerController.clearYouTubeQueue()
        playerController.clearPlaylistQueue()
        playerController.play(track)
    }

    fun clearAllRecentlyPlayed() {
        recentlyPlayedManager.clearAll()
    }
}
