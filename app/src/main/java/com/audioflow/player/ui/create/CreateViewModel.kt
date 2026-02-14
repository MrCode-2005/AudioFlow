package com.audioflow.player.ui.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.local.PlaylistManager
import com.audioflow.player.data.local.TrackMetadataManager
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.model.Track
import com.audioflow.player.model.TrackSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CreateViewModel"

// Max duration for individual songs (10 minutes in milliseconds)
private const val MAX_SONG_DURATION_MS = 10 * 60 * 1000L

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val playlistManager: PlaylistManager,
    private val mediaRepository: MediaRepository,
    private val trackMetadataManager: TrackMetadataManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                val playlist = playlistManager.createPlaylist(name)
                _uiState.value = _uiState.value.copy(generatedPlaylistId = playlist.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating playlist", e)
            }
        }
    }

    fun generateCustomPlaylist(
        artist: String, 
        selectedGenre: String, 
        customGenre: String, 
        songCount: Int,
        includePlaylist: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            try {
                val artistTrimmed = artist.trim()
                val genreTrimmed = customGenre.ifBlank { selectedGenre }.trim()
                
                // Build more specific search queries for better relevance
                val searchQueries = buildSearchQueries(artistTrimmed, genreTrimmed)
                
                if (searchQueries.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = "Please enter at least an artist name or genre"
                    )
                    return@launch
                }
                
                Log.d(TAG, "Searching with queries: $searchQueries")
                
                // Collect results from multiple search queries for more results
                val allResults = mutableListOf<com.audioflow.player.data.remote.YouTubeSearchResult>()
                val seenVideoIds = mutableSetOf<String>()
                
                for (query in searchQueries) {
                    if (allResults.size >= songCount) break
                    
                    Log.d(TAG, "Searching YouTube for: $query")
                    val result = mediaRepository.searchYouTube(query)
                    
                    result.onSuccess { searchResults ->
                        for (item in searchResults) {
                            if (seenVideoIds.contains(item.videoId)) continue
                            
                            // Filter by duration if not including playlists
                            if (!includePlaylist && item.duration > MAX_SONG_DURATION_MS) {
                                Log.d(TAG, "Skipping long video: ${item.title} (${item.duration / 60000}min)")
                                continue
                            }
                            
                            // Filter for relevance - check if title/artist matches keywords
                            if (isRelevantResult(item, artistTrimmed, genreTrimmed)) {
                                seenVideoIds.add(item.videoId)
                                allResults.add(item)
                            }
                        }
                    }
                }
                
                // If still need more songs, do another broader search
                if (allResults.size < songCount) {
                    val broadQuery = when {
                        artistTrimmed.isNotBlank() -> "$artistTrimmed popular songs"
                        genreTrimmed.isNotBlank() -> "$genreTrimmed music 2024"
                        else -> "popular songs 2024"
                    }
                    
                    mediaRepository.searchYouTube(broadQuery).onSuccess { searchResults ->
                        for (item in searchResults) {
                            if (allResults.size >= songCount) return@onSuccess
                            if (seenVideoIds.contains(item.videoId)) continue
                            
                            if (!includePlaylist && item.duration > MAX_SONG_DURATION_MS) continue
                            
                            seenVideoIds.add(item.videoId)
                            allResults.add(item)
                        }
                    }
                }
                
                val tracks = allResults.take(songCount).map { searchResult ->
                    Track(
                        id = "yt_${searchResult.videoId}",
                        title = searchResult.title,
                        artist = searchResult.artist,
                        album = "YouTube",
                        duration = searchResult.duration,
                        artworkUri = android.net.Uri.parse(searchResult.thumbnailUrl),
                        contentUri = android.net.Uri.parse("yt_stream://${searchResult.videoId}"),
                        source = TrackSource.YOUTUBE,
                        dateAdded = System.currentTimeMillis()
                    )
                }

                if (tracks.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = "No songs found for your search"
                    )
                    return@launch
                }

                // Save metadata for later ID resolution
                trackMetadataManager.saveTracks(tracks)

                // Generate playlist name
                val playlistName = when {
                    artistTrimmed.isNotBlank() && genreTrimmed.isNotBlank() -> 
                        "$artistTrimmed $genreTrimmed Mix"
                    artistTrimmed.isNotBlank() -> 
                        "$artistTrimmed Mix"
                    else -> 
                        "$genreTrimmed Mix"
                }
                
                val playlist = playlistManager.createPlaylist(playlistName)

                // Use batch add for fewer I/O operations and UI updates
                playlistManager.addTracksToPlaylist(playlist.id, tracks)

                Log.d(TAG, "Created playlist '${playlistName}' with ${tracks.size} songs")
                
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    generatedPlaylistId = playlist.id,
                    navigateToLibrary = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error generating playlist", e)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Build multiple specific search queries for better results
     */
    private fun buildSearchQueries(artist: String, genre: String): List<String> {
        val queries = mutableListOf<String>()
        
        when {
            artist.isNotBlank() && genre.isNotBlank() -> {
                queries.add("$artist $genre official audio")
                queries.add("$artist $genre songs")
                queries.add("$artist best $genre")
            }
            artist.isNotBlank() -> {
                queries.add("$artist official audio")
                queries.add("$artist songs")
                queries.add("$artist best songs")
                queries.add("$artist top hits")
            }
            genre.isNotBlank() -> {
                queries.add("$genre songs official audio")
                queries.add("best $genre songs 2024")
                queries.add("$genre music")
                queries.add("top $genre hits")
            }
        }
        
        return queries
    }
    
    /**
     * Check if a search result is relevant to the user's query
     */
    private fun isRelevantResult(
        result: com.audioflow.player.data.remote.YouTubeSearchResult,
        artist: String,
        genre: String
    ): Boolean {
        val titleLower = result.title.lowercase()
        val artistLower = result.artist.lowercase()
        
        // Filter out common non-song content
        val excludedTerms = listOf(
            "interview", "behind the scenes", "making of", "documentary",
            "podcast", "vlog", "reaction", "tutorial", "lesson", "cover by",
            "karaoke", "instrumental only"
        )
        
        for (term in excludedTerms) {
            if (titleLower.contains(term)) return false
        }
        
        // If artist is specified, prioritize matches
        if (artist.isNotBlank()) {
            val artistQuery = artist.lowercase()
            if (titleLower.contains(artistQuery) || artistLower.contains(artistQuery)) {
                return true
            }
        }
        
        // If genre is specified, check for music-related content
        if (genre.isNotBlank()) {
            val genreQuery = genre.lowercase()
            // More lenient for genre - just avoid excluded terms
            if (titleLower.contains(genreQuery) || 
                titleLower.contains("song") || 
                titleLower.contains("music") ||
                titleLower.contains("audio") ||
                titleLower.contains("official")) {
                return true
            }
        }
        
        // Default: accept if it looks like music
        return titleLower.contains("official") || 
               titleLower.contains("audio") || 
               titleLower.contains("lyric") ||
               titleLower.contains("music video") ||
               artistLower.contains("vevo") ||
               artistLower.contains("records") ||
               artistLower.contains("music")
    }

    fun clearGeneratedState() {
        _uiState.value = _uiState.value.copy(generatedPlaylistId = null, error = null, navigateToLibrary = false)
    }
}

data class CreateUiState(
    val isGenerating: Boolean = false,
    val generatedPlaylistId: String? = null,
    val navigateToLibrary: Boolean = false,
    val error: String? = null
)
