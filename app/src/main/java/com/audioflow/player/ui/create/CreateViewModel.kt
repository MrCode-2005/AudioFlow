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

    fun generateCustomPlaylist(artist: String, selectedGenre: String, customGenre: String, songCount: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            try {
                // Build search query dynamically based on filled fields
                val artistTrimmed = artist.trim()
                val genreTrimmed = customGenre.ifBlank { selectedGenre }.trim()
                
                val query = when {
                    artistTrimmed.isNotBlank() && genreTrimmed.isNotBlank() -> 
                        "$artistTrimmed $genreTrimmed songs"
                    artistTrimmed.isNotBlank() -> 
                        "$artistTrimmed songs official audio"
                    genreTrimmed.isNotBlank() -> 
                        "$genreTrimmed songs music"
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            isGenerating = false,
                            error = "Please enter at least an artist name or genre"
                        )
                        return@launch
                    }
                }
                
                Log.d(TAG, "Searching YouTube for: $query")
                val result = mediaRepository.searchYouTube(query)
                
                result.onSuccess { searchResults ->
                    // Filter results to prioritize artist songs if artist is specified
                    val filteredResults = if (artistTrimmed.isNotBlank()) {
                        searchResults
                            .filter { result ->
                                result.title.contains(artistTrimmed, ignoreCase = true) ||
                                result.artist.contains(artistTrimmed, ignoreCase = true)
                            }
                            .ifEmpty { searchResults } // Fallback to all results if filter is too strict
                    } else {
                        searchResults
                    }
                    
                    val tracks = filteredResults.take(songCount).map { searchResult ->
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
                        return@onSuccess
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
                    playlistManager.addTracksToPlaylist(playlist.id, tracks.map { it.id })

                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generatedPlaylistId = playlist.id,
                        navigateToLibrary = true
                    )
                }.onFailure { e ->
                    Log.e(TAG, "Search failed", e)
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = "Failed to find songs: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating playlist", e)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = e.message
                )
            }
        }
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
