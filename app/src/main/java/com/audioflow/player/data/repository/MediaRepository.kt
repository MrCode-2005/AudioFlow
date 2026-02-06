package com.audioflow.player.data.repository

import android.net.Uri
import com.audioflow.player.data.local.LocalMusicScanner
import com.audioflow.player.data.remote.YouTubeExtractor
import com.audioflow.player.data.remote.YouTubeMetadataFetcher
import com.audioflow.player.data.remote.YouTubeSearchResult
import com.audioflow.player.data.remote.YouTubeStreamInfo
import com.audioflow.player.model.Album
import com.audioflow.player.model.Artist
import com.audioflow.player.model.Track
import com.audioflow.player.model.TrackSource
import com.audioflow.player.model.YouTubeMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val localMusicScanner: LocalMusicScanner,
    private val youTubeMetadataFetcher: YouTubeMetadataFetcher,
    private val youTubeExtractor: YouTubeExtractor,
    private val trackMetadataManager: com.audioflow.player.data.local.TrackMetadataManager
) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: Flow<List<Track>> = _tracks.asStateFlow()
    
    // ... existing defined flows ...
    
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: Flow<List<Album>> = _albums.asStateFlow()
    
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: Flow<List<Artist>> = _artists.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()
    
    suspend fun refreshLocalMusic() {
        _isLoading.value = true
        try {
            _tracks.value = localMusicScanner.scanAllTracks()
            _albums.value = localMusicScanner.getAlbums()
            _artists.value = localMusicScanner.getArtists()
        } catch (e: Exception) {
            e.printStackTrace()
            // If scanning fails, we keep empty lists or previous state
            // and maybe trigger an error state if possible
        } finally {
            _isLoading.value = false
        }
    }
    
    fun getAllTracks(): List<Track> = _tracks.value
    
    fun getAllAlbums(): List<Album> = _albums.value
    
    fun getAllArtists(): List<Artist> = _artists.value
    
    fun getTrackById(id: String): Track? {
        // Try local tracks first
        return _tracks.value.find { it.id == id } 
            // Fallback to cached remote tracks
            ?: trackMetadataManager.getTrack(id)
    }
    
    fun getAlbumById(id: String): Album? = _albums.value.find { it.id == id }
    
    fun getArtistById(id: String): Artist? = _artists.value.find { it.id == id }
    
    fun getTracksForArtist(artistName: String): List<Track> = 
        _tracks.value.filter { it.artist.equals(artistName, ignoreCase = true) }
    
    fun getTracksForAlbum(albumName: String): List<Track> =
        _tracks.value.filter { it.album.equals(albumName, ignoreCase = true) }
    
    fun searchTracks(query: String): List<Track> {
        val lowercaseQuery = query.lowercase()
        return _tracks.value.filter {
            it.title.lowercase().contains(lowercaseQuery) ||
            it.artist.lowercase().contains(lowercaseQuery) ||
            it.album.lowercase().contains(lowercaseQuery)
        }
    }
    
    fun searchAlbums(query: String): List<Album> {
        val lowercaseQuery = query.lowercase()
        return _albums.value.filter {
            it.name.lowercase().contains(lowercaseQuery) ||
            it.artist.lowercase().contains(lowercaseQuery)
        }
    }
    
    fun searchArtists(query: String): List<Artist> {
        val lowercaseQuery = query.lowercase()
        return _artists.value.filter {
            it.name.lowercase().contains(lowercaseQuery)
        }
    }
    
    // YouTube Integration
    fun isValidYouTubeUrl(url: String): Boolean = youTubeMetadataFetcher.isValidYouTubeUrl(url)
    
    suspend fun fetchYouTubeMetadata(url: String): Result<YouTubeMetadata> =
        youTubeMetadataFetcher.fetchMetadata(url)
    
    fun getYouTubeThumbnailUrl(videoId: String): String =
        youTubeMetadataFetcher.getThumbnailUrl(videoId)
    
    /**
     * Search YouTube for videos matching the query
     */
    suspend fun searchYouTube(query: String): Result<List<YouTubeSearchResult>> =
        youTubeExtractor.search(query)
    
    /**
     * Extract playable audio stream URL for a YouTube video
     */
    suspend fun getYouTubeStreamUrl(videoId: String): Result<YouTubeStreamInfo> =
        youTubeExtractor.extractStreamUrl(videoId)
    
    /**
     * Create a Track object from YouTube stream info for playback
     */
    fun createTrackFromYouTube(streamInfo: YouTubeStreamInfo): Track {
        return Track(
            id = "yt_${streamInfo.videoId}",
            title = streamInfo.title,
            artist = streamInfo.artist,
            album = "YouTube",
            duration = streamInfo.duration,
            artworkUri = Uri.parse(streamInfo.thumbnailUrl),
            contentUri = Uri.parse(streamInfo.audioStreamUrl),
            source = TrackSource.YOUTUBE,
            dateAdded = System.currentTimeMillis()
        )
    }
    
    // Delete track from device
    suspend fun deleteTrack(trackId: String): Boolean {
        val success = localMusicScanner.deleteTrack(trackId)
        if (success) {
            // Refresh the tracks list after deletion
            refreshLocalMusic()
        }
        return success
    }
}
