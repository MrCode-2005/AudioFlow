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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentUris
import android.util.Log
import javax.inject.Inject

private const val TAG = "SearchViewModel"

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
    private val recentlyPlayedManager: RecentlyPlayedManager,
    val filterPreferences: com.audioflow.player.data.local.FilterPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    val playbackState = playerController.playbackState
    val searchHistory = searchHistoryManager.history
    val recentlyPlayedSongs = recentlyPlayedManager.recentSongs
    
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null
    
    fun updateQuery(query: String) {
        // Update query text but DON'T clear results while typing
        _uiState.value = _uiState.value.copy(
            query = query,
            shouldDismissKeyboard = false
        )
        
        // Cancel previous search AND prefetch jobs
        searchJob?.cancel()
        prefetchJob?.cancel()
        
        if (query.isBlank()) {
            // Clear results when query is empty
            _uiState.value = _uiState.value.copy(
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                youtubeResults = emptyList(),
                filteredResults = emptyList(),
                youtubeMetadata = null,
                youtubeError = null,
                isSearching = false,
                hasResults = false
            )
            return
        }
        
        // Show loading indicator but KEEP old results visible while typing
        _uiState.value = _uiState.value.copy(isSearching = true)
        
        // PROPER DEBOUNCE: 600ms for YouTube (wait for user to finish typing)
        // This prevents searching for "S", "St", "Sta", "Star" etc.
        // Only fires search after user stops typing for 600ms
        val debounceTime = if (_uiState.value.searchMode == SearchMode.YOUTUBE) 600L else 150L
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
            filteredResults = emptyList(),
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
                filteredResults = emptyList(),
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
                filteredResults = emptyList(),
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
            filteredResults = emptyList(),
            youtubeMetadata = null,
            isSearching = false,
            hasResults = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
        )
    }
    
    private fun performYouTubeSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isYouTubeLoading = true)
            
            // Augment query with language/genre filter suffix
            val filterSuffix = filterPreferences.getFilterSuffix()
            val augmentedQuery = if (filterSuffix.isNotBlank()) "$query $filterSuffix" else query
            Log.d(TAG, "Searching YouTube with query: $augmentedQuery (original: $query)")
            
            mediaRepository.searchYouTube(augmentedQuery)
                .onSuccess { results ->
                    // Save to search history on successful search
                    searchHistoryManager.addSearch(query)
                    
                    // RAW RESULTS: No filtering - show exactly what YouTube returns
                    Log.d(TAG, "Search returned ${results.size} raw results (no filtering)")
                    
                    _uiState.value = _uiState.value.copy(
                        youtubeResults = results,
                        filteredResults = results,
                        tracks = emptyList(),
                        albums = emptyList(),
                        artists = emptyList(),
                        youtubeMetadata = null,
                        isSearching = false,
                        isYouTubeLoading = false,
                        hasResults = results.isNotEmpty(),
                        shouldDismissKeyboard = true
                    )
                    
                    // Set queue for navigation
                    if (results.isNotEmpty()) {
                        playerController.setYouTubeQueue(results, -1)
                        
                        // PREFETCH: Cancel any old prefetch and start new one
                        // Only prefetch after FINAL results are shown
                        prefetchJob?.cancel()
                        prefetchJob = prefetchFirstSearchResults(results.take(10))
                    }
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
                    filteredResults = emptyList(),
                    youtubeError = friendlyMessage,
                    isSearching = false,
                    isYouTubeLoading = false,
                    hasResults = false
                )
            }
        }
    }
    
    /**
     * Filter search results for relevance - remove vlogs, interviews, unrelated content
     */
    private fun filterForRelevance(results: List<YouTubeSearchResult>, query: String): List<YouTubeSearchResult> {
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(" ").filter { it.length > 2 }
        
        // Negative filters - these indicate non-music content
        val excludedTerms = setOf(
            "interview", "behind the scenes", "making of", "documentary",
            "podcast", "vlog", "reaction", "tutorial", "lesson", "how to",
            "unboxing", "review", "trailer", "movie clip", "gameplay",
            "live stream", "livestream", "streaming", "q&a", "talk show", "news", 
            "compilation funny", "full movie", "movie scene", "episode", "ep.",
            "series", "season", "chapter", "audiobook", "asmr"
        )
        
        // Positive signals - these indicate actual music
        val musicIndicators = setOf(
            "official", "audio", "lyrics", "lyric", "music video", 
            "mv", "song", "track", "album", "single", "ft.", "feat.",
            "remix", "cover", "acoustic", "live performance"
        )
        
        return results.filter { result ->
            val titleLower = result.title.lowercase()
            val artistLower = result.artist.lowercase()
            
            // Check for excluded terms
            val hasExcludedTerm = excludedTerms.any { term -> 
                titleLower.contains(term) 
            }
            if (hasExcludedTerm) {
                Log.d(TAG, "Filtering out (excluded term): ${result.title}")
                return@filter false
            }
            
            // Duration filter - videos > 15 min are likely not songs (use 15 min per requirements)
            if (result.duration > 15 * 60 * 1000L) {
                Log.d(TAG, "Filtering out (too long > 15min): ${result.title}")
                return@filter false
            }
            
            // Check relevance to query
            val hasQueryWord = queryWords.any { word ->
                titleLower.contains(word) || artistLower.contains(word)
            }
            
            // Check for music indicators
            val hasMusicIndicator = musicIndicators.any { indicator ->
                titleLower.contains(indicator)
            }
            
            // Accept if:
            // 1. Title contains query words, OR
            // 2. Has music indicators, OR  
            // 3. Artist contains query words
            // 4. Is from known music channels (contains "vevo", "records", "music", etc)
            val isFromMusicChannel = artistLower.contains("vevo") || 
                                     artistLower.contains("records") ||
                                     artistLower.contains("music") ||
                                     artistLower.contains("official")
            
            val isRelevant = hasQueryWord || hasMusicIndicator || isFromMusicChannel
            
            if (!isRelevant) {
                Log.d(TAG, "Filtering out (not relevant): ${result.title}")
            }
            
            isRelevant
        }.sortedWith { a, b ->
            // YouTube-like relevance sorting: prioritize songs, remixes, official tracks
            val aLower = a.title.lowercase()
            val bLower = b.title.lowercase()
            val aArtist = a.artist.lowercase()
            val bArtist = b.artist.lowercase()
            
            // Score each result (higher = better)
            fun score(title: String, artist: String): Int {
                var s = 0
                
                // Query word match is most important (like YouTube)
                if (queryWords.any { title.contains(it) }) s += 100
                if (queryWords.any { artist.contains(it) }) s += 80
                
                // Official content is prioritized (like YouTube)
                if (title.contains("official")) s += 50
                if (title.contains("official audio")) s += 30
                if (title.contains("official music video") || title.contains("official video")) s += 25
                
                // Music content types (songs + remixes as user requested)
                if (title.contains("audio")) s += 20
                if (title.contains("lyrics") || title.contains("lyric")) s += 15
                if (title.contains("remix")) s += 15  // Keep remixes as user requested
                if (title.contains("song")) s += 10
                if (title.contains("music video") || title.contains("mv")) s += 10
                if (title.contains("ft.") || title.contains("feat.")) s += 5 // Collaborations
                if (title.contains("cover")) s += 5
                if (title.contains("acoustic")) s += 5
                
                // Known music channels get boost
                if (artist.contains("vevo")) s += 40
                if (artist.contains("records")) s += 20
                if (artist.contains("official")) s += 15
                
                return s
            }
            
            val aScore = score(aLower, aArtist)
            val bScore = score(bLower, bArtist)
            
            // Higher score first
            bScore.compareTo(aScore)
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
    
    /**
     * PREFETCH: Pre-extract URLs for instant next/prev playback.
     * Returns the Job so it can be cancelled when a new search starts.
     * Uses sequential extraction to avoid hogging yt-dlp.
     */
    private fun prefetchFirstSearchResults(results: List<YouTubeSearchResult>): Job {
        return viewModelScope.launch {
            Log.d(TAG, "Prefetching ${results.size} search results...")
            
            // Extract sequentially (not parallel!) to avoid blocking yt-dlp
            // If user starts new search, this job gets cancelled
            for (result in results) {
                try {
                    mediaRepository.getYouTubeStreamUrl(result.videoId)
                        .onSuccess {
                            Log.d(TAG, "✓ Prefetched: ${result.title}")
                        }
                        .onFailure {
                            Log.w(TAG, "✗ Prefetch failed: ${result.title}")
                        }
                } catch (e: CancellationException) {
                    Log.d(TAG, "Prefetch cancelled (new search started)")
                    throw e // Re-throw to properly cancel
                }
            }
            
            Log.d(TAG, "All prefetches completed")
        }
    }
}
