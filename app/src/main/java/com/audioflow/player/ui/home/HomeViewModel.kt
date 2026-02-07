package com.audioflow.player.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audioflow.player.data.local.RecentlyPlayedManager
import com.audioflow.player.data.local.RecentlyPlayedSong
import com.audioflow.player.data.remote.YouTubeSearchResult
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.model.Album
import com.audioflow.player.model.Track
import com.audioflow.player.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import javax.inject.Inject

/**
 * Genre category for recommendation sections
 */
data class GenreCategory(
    val name: String,
    val query: String,
    val emoji: String,
    val songs: List<YouTubeSearchResult> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * Auto-generated playlist based on trends
 */
data class TrendingPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val songs: List<YouTubeSearchResult> = emptyList(),
    val coverEmoji: String = "🎵"
)

data class HomeUiState(
    val recentTracks: List<Track> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDynamicMode: Boolean = false,
    
    // Recommendations
    val trendingSongs: List<YouTubeSearchResult> = emptyList(),
    val isTrendingLoading: Boolean = false,
    val genreCategories: List<GenreCategory> = emptyList(),
    val trendingPlaylists: List<TrendingPlaylist> = emptyList(),
    val isRecommendationsLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playerController: PlayerController,
    private val recentlyPlayedManager: RecentlyPlayedManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    val playbackState = playerController.playbackState
    
    // Recently played songs from manager
    val recentlyPlayedSongs: StateFlow<List<RecentlyPlayedSong>> = recentlyPlayedManager.recentSongs
    
    // Predefined genre categories
    private val defaultGenres = listOf(
        GenreCategory("Pop Hits", "pop music 2024 hits", "🎤"),
        GenreCategory("Hip-Hop", "hip hop rap 2024 trending", "🎧"),
        GenreCategory("R&B Soul", "r&b soul music 2024", "💜"),
        GenreCategory("Rock", "rock music hits 2024", "🎸"),
        GenreCategory("EDM", "edm electronic dance music 2024", "🎹"),
        GenreCategory("Latin", "latin reggaeton 2024 hits", "💃"),
        GenreCategory("Indie", "indie alternative music 2024", "🌙"),
        GenreCategory("K-Pop", "kpop korean music 2024", "⭐")
    )
    
    init {
        // Initialize genres without songs
        _uiState.value = _uiState.value.copy(
            genreCategories = defaultGenres
        )
    }
    
    fun loadMusic() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                mediaRepository.refreshLocalMusic()
                
                val allTracks = mediaRepository.getAllTracks()
                val allAlbums = mediaRepository.getAllAlbums()
                
                _uiState.value = _uiState.value.copy(
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
    
    /**
     * Load trending songs and recommendations
     */
    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRecommendationsLoading = true,
                isTrendingLoading = true
            )
            
            // Load trending songs
            loadTrendingSongs()
            
            // Generate auto-playlists
            generateTrendingPlaylists()
        }
    }
    
    private suspend fun loadTrendingSongs() {
        mediaRepository.searchYouTube("trending music 2024 hits")
            .onSuccess { results ->
                _uiState.value = _uiState.value.copy(
                    trendingSongs = results.take(10),
                    isTrendingLoading = false
                )
                Log.d("HomeViewModel", "Loaded ${results.size} trending songs")
            }
            .onFailure { error ->
                Log.e("HomeViewModel", "Failed to load trending: ${error.message}")
                _uiState.value = _uiState.value.copy(isTrendingLoading = false)
            }
    }
    
    /**
     * Load songs for a specific genre category
     */
    fun loadGenreCategory(index: Int) {
        if (index < 0 || index >= _uiState.value.genreCategories.size) return
        
        val category = _uiState.value.genreCategories[index]
        if (category.songs.isNotEmpty() || category.isLoading) return // Already loaded or loading
        
        // Mark as loading
        val updatedCategories = _uiState.value.genreCategories.toMutableList()
        updatedCategories[index] = category.copy(isLoading = true)
        _uiState.value = _uiState.value.copy(genreCategories = updatedCategories)
        
        viewModelScope.launch {
            mediaRepository.searchYouTube(category.query)
                .onSuccess { results ->
                    val categories = _uiState.value.genreCategories.toMutableList()
                    categories[index] = category.copy(
                        songs = results.take(8),
                        isLoading = false
                    )
                    _uiState.value = _uiState.value.copy(genreCategories = categories)
                    Log.d("HomeViewModel", "Loaded ${results.size} songs for ${category.name}")
                }
                .onFailure { error ->
                    val categories = _uiState.value.genreCategories.toMutableList()
                    categories[index] = category.copy(isLoading = false)
                    _uiState.value = _uiState.value.copy(genreCategories = categories)
                    Log.e("HomeViewModel", "Failed to load ${category.name}: ${error.message}")
                }
        }
    }
    
    /**
     * Generate auto-playlists based on current trends
     */
    private suspend fun generateTrendingPlaylists() {
        val playlists = mutableListOf<TrendingPlaylist>()
        
        // Define auto playlist queries
        val playlistConfigs = listOf(
            Triple("today_hits", "Today's Top Hits", "Most played songs right now"),
            Triple("chill_vibes", "Chill Vibes", "Relax and unwind"),
            Triple("workout", "Workout Beats", "Energy for your workout"),
            Triple("party", "Party Mix", "Get the party started")
        )
        
        // Fetch songs for each playlist in parallel
        val deferredResults = playlistConfigs.map { (id, name, desc) ->
            viewModelScope.async {
                val query = when (id) {
                    "today_hits" -> "top hits 2024 popular songs"
                    "chill_vibes" -> "chill lofi relaxing music"
                    "workout" -> "workout gym motivation music"
                    "party" -> "party dance music 2024"
                    else -> "popular music 2024"
                }
                
                val emoji = when (id) {
                    "today_hits" -> "🔥"
                    "chill_vibes" -> "😌"
                    "workout" -> "💪"
                    "party" -> "🎉"
                    else -> "🎵"
                }
                
                mediaRepository.searchYouTube(query)
                    .fold(
                        onSuccess = { results ->
                            TrendingPlaylist(
                                id = id,
                                name = name,
                                description = desc,
                                songs = results.take(15),
                                coverEmoji = emoji
                            )
                        },
                        onFailure = {
                            TrendingPlaylist(id = id, name = name, description = desc, coverEmoji = emoji)
                        }
                    )
            }
        }
        
        val results = deferredResults.awaitAll()
        _uiState.value = _uiState.value.copy(
            trendingPlaylists = results.filter { it.songs.isNotEmpty() },
            isRecommendationsLoading = false
        )
        Log.d("HomeViewModel", "Generated ${results.count { it.songs.isNotEmpty() }} playlists")
    }
    
    /**
     * Play a YouTube search result from recommendations
     */
    fun playYouTubeResult(result: YouTubeSearchResult, queue: List<YouTubeSearchResult> = emptyList()) {
        val index = queue.indexOf(result).takeIf { it >= 0 } ?: 0
        val queueToUse = queue.ifEmpty { listOf(result) }
        
        playerController.setYouTubeQueue(queueToUse, index)
        
        // Extract and play
        viewModelScope.launch {
            mediaRepository.getYouTubeStreamUrl(result.videoId)
                .onSuccess { streamInfo ->
                    val track = mediaRepository.createTrackFromYouTube(streamInfo)
                    playerController.play(track)
                }
                .onFailure { error ->
                    Log.e("HomeViewModel", "Failed to play: ${error.message}")
                }
        }
    }
    
    /**
     * Play all songs in a trending playlist
     */
    fun playTrendingPlaylist(playlist: TrendingPlaylist, startIndex: Int = 0) {
        if (playlist.songs.isEmpty()) return
        playerController.setYouTubeQueue(playlist.songs, startIndex)
        playYouTubeResult(playlist.songs[startIndex], playlist.songs)
    }
    
    /**
     * Play all songs in a genre category
     */
    fun playGenreCategory(category: GenreCategory, startIndex: Int = 0) {
        if (category.songs.isEmpty()) return
        playerController.setYouTubeQueue(category.songs, startIndex)
        playYouTubeResult(category.songs[startIndex], category.songs)
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
    
    fun toggleContentMode() {
        _uiState.value = _uiState.value.copy(
            isDynamicMode = !_uiState.value.isDynamicMode
        )
    }
}
