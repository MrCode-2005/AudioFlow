package com.audioflow.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audioflow.player.data.remote.YouTubeSearchResult
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.model.PlaybackState
import com.audioflow.player.model.RepeatMode
import com.audioflow.player.model.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.audioflow.player.data.local.RecentlyPlayedManager

private const val TAG = "PlayerController"

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val recentlyPlayedManager: RecentlyPlayedManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
    private var currentQueue: List<Track> = emptyList()
    private var isControllerReady = false
    
    // YouTube search results queue for next/previous navigation
    private var youTubeQueue: List<YouTubeSearchResult> = emptyList()
    private var youTubeQueueIndex: Int = -1
    // Full Track stubs with thumbnails for YouTube queue (enables carousel prev/next art)
    private var youTubeQueueTracks: MutableList<Track> = mutableListOf()
    
    // Playlist queue for mixed/remote tracks that need lazy loading
    private var playlistQueue: List<Track> = emptyList()
    private var playlistQueueIndex: Int = -1
    
    private var isExtractingStream: Boolean = false
    
    // Cache for extracted stream URLs to avoid re-extraction
    private val streamUrlCache = mutableMapOf<String, CachedStreamUrl>()
    private data class CachedStreamUrl(val url: String, val timestamp: Long, val mimeType: String? = null)
    private val CACHE_DURATION_MS = 15 * 60 * 1000L // 15 minutes - longer cache for faster playback
    
    // PRE-PREPARED TRACKS: Ready-to-play Track objects with resolved URLs
    private val preparedTracks = mutableMapOf<String, Track>()
    
    // Pre-built MediaItems ready for instant queue insertion
    private val preparedMediaItems = mutableMapOf<String, MediaItem>()
    
    /**
     * Create a MediaItem with proper MIME type hints for YouTube streams.
     * This is essential for ExoPlayer to properly decode YouTube audio on real devices.
     */
    @OptIn(UnstableApi::class)
    private fun createMediaItemForTrack(track: Track): MediaItem {
        val isYouTube = track.source == com.audioflow.player.model.TrackSource.YOUTUBE
        val uri = track.contentUri
        
        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri)
                    .build()
            )
        
        // For YouTube streams, set MIME type hint to help ExoPlayer decode properly
        if (isYouTube) {
            val urlString = uri.toString()
            val mimeType = when {
                urlString.contains("mime=audio%2Fwebm") || urlString.contains("mime=audio/webm") -> MimeTypes.AUDIO_WEBM
                urlString.contains("mime=audio%2Fmp4") || urlString.contains("mime=audio/mp4") -> MimeTypes.AUDIO_MP4
                urlString.contains(".m4a") -> MimeTypes.AUDIO_MP4
                urlString.contains(".webm") -> MimeTypes.AUDIO_WEBM
                urlString.contains(".opus") -> MimeTypes.AUDIO_OPUS
                // Default to MP4 for googlevideo.com URLs
                else -> MimeTypes.AUDIO_MP4
            }
            Log.d(TAG, "YouTube stream MIME type hint: $mimeType")
            builder.setMimeType(mimeType)
        }
        
        return builder.build()
    }
    
    init {
        // Initialize immediately
        initializeController()
    }
    
    private fun initializeController() {
        try {
            Log.d(TAG, "Initializing MediaController...")
            val sessionToken = SessionToken(
                context,
                ComponentName(context, MusicService::class.java)
            )
            
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    mediaController = controllerFuture?.get()
                    isControllerReady = true
                    Log.d(TAG, "MediaController ready")
                    setupPlayerListener()
                    startPositionUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get MediaController: ${e.message}")
                    e.printStackTrace()
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize controller: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Play a playlist with support for lazy-loading remote tracks
     */
    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0) {
        Log.d(TAG, "playPlaylist() called with ${tracks.size} tracks, startIndex: $startIndex")
        
        // Clear YouTube queue to avoid confusion
        clearYouTubeQueue()
        playlistQueue = tracks
        playlistQueueIndex = startIndex
        
        playPlaylistQueueItem(startIndex)
    }
    
    private fun playPlaylistQueueItem(index: Int) {
        if (index < 0 || index >= playlistQueue.size || isExtractingStream) return
        
        val track = playlistQueue[index]
        playlistQueueIndex = index
        Log.d(TAG, "Playing playlist item: ${track.title}, URI: ${track.contentUri}")
        
        // Check if this is a YouTube track that needs extraction
        val needsExtraction = track.source == com.audioflow.player.model.TrackSource.YOUTUBE && 
            (track.contentUri.toString().startsWith("yt_stream") || !track.contentUri.toString().startsWith("http"))
            
        if (needsExtraction) {
            val videoId = track.id.removePrefix("yt_")
            
            // Check cache first
            val cached = streamUrlCache[videoId]
            if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
                Log.d(TAG, "Using cached stream URL for $videoId")
                val cachedTrack = track.copy(contentUri = Uri.parse(cached.url))
                playSingleTrackInternal(cachedTrack)
                prefetchNextTrack(index)
                return
            }
            
            Log.d(TAG, "Track needs extraction. Extracting stream...")
            isExtractingStream = true
            
            scope.launch {
                mediaRepository.getYouTubeStreamUrl(videoId)
                    .onSuccess { streamInfo ->
                        // Cache the URL
                        streamUrlCache[videoId] = CachedStreamUrl(
                            url = streamInfo.audioStreamUrl,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        val playableTrack = mediaRepository.createTrackFromYouTube(streamInfo)
                        playSingleTrackInternal(playableTrack)
                        isExtractingStream = false
                        
                        // Prefetch next track
                        prefetchNextTrack(index)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to extract stream: ${error.message}")
                        _error.value = "Failed to play: ${error.message}"
                        isExtractingStream = false
                    }
            }
        } else {
            // Local track or already valid
            playSingleTrackInternal(track)
            prefetchNextTrack(index)
        }
    }
    
    private fun prefetchNextTrack(currentIndex: Int) {
        val nextIndex = currentIndex + 1
        if (nextIndex >= playlistQueue.size) return
        
        val nextTrack = playlistQueue[nextIndex]
        val needsExtraction = nextTrack.source == com.audioflow.player.model.TrackSource.YOUTUBE && 
            (nextTrack.contentUri.toString().startsWith("yt_stream") || !nextTrack.contentUri.toString().startsWith("http"))
        
        if (!needsExtraction) return
        
        val videoId = nextTrack.id.removePrefix("yt_")
        val cached = streamUrlCache[videoId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
            return // Already cached
        }
        
        Log.d(TAG, "Prefetching next track: ${nextTrack.title}")
        scope.launch {
            mediaRepository.getYouTubeStreamUrl(videoId)
                .onSuccess { streamInfo ->
                    streamUrlCache[videoId] = CachedStreamUrl(
                        url = streamInfo.audioStreamUrl,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Prefetched stream URL for ${nextTrack.title}")
                }
                .onFailure { error ->
                    Log.w(TAG, "Prefetch failed for ${nextTrack.title}: ${error.message}")
                }
        }
    }
    
    private fun playSingleTrackInternal(track: Track) {
        currentQueue = listOf(track) // ExoPlayer only sees one track at a time in lazy mode
        
        // Save to recently played
        recentlyPlayedManager.addSong(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            thumbnailUri = track.artworkUri,
            duration = track.duration
        )
        
        val mediaItem = createMediaItemForTrack(track)
        
        mediaController?.apply {
            stop()
            clearMediaItems()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            play()
        }
    }
    
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "isPlaying changed: $isPlaying")
                updatePlaybackState()
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TAG, "Media item transition: ${mediaItem?.mediaId}")
                updatePlaybackState()
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Playback state changed: $stateName")
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                updatePlaybackState()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}")
                Log.e(TAG, "Error code: ${error.errorCode}")
                _error.value = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Network error. Check your connection and try again."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Stream unavailable. Please try another song."
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Unable to decode audio. Trying next song..."
                    else -> error.message ?: "Playback error occurred"
                }
                // Don't crash — try to auto-advance to next song on decoder errors
                if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED) {
                    scope.launch {
                        kotlinx.coroutines.delay(500)
                        next()
                    }
                }
            }
            
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updatePlaybackState()
            }
            
            override fun onRepeatModeChanged(repeatMode: Int) {
                updatePlaybackState()
            }
        })
    }
    
    private fun startPositionUpdates() {
        scope.launch {
            while (isActive) {
                if (mediaController?.isPlaying == true) {
                    updatePlaybackState()
                }
                delay(100) // Update every 100ms for smooth lyrics sync
            }
        }
    }
    
    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        
        try {
            _playbackState.value = PlaybackState(
                isPlaying = controller.isPlaying,
                currentTrack = if (currentIndex >= 0 && currentIndex < currentQueue.size) {
                    currentQueue[currentIndex]
                } else if (currentQueue.isNotEmpty()) {
                    currentQueue[0] // Fallback to first track
                } else null,
                currentPosition = controller.currentPosition.coerceAtLeast(0),
                duration = controller.duration.takeIf { it > 0 } ?: 0,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
                // Use full YouTube queue tracks for carousel display (shows prev/next album art)
                queue = if (youTubeQueueTracks.isNotEmpty()) youTubeQueueTracks else currentQueue,
                currentQueueIndex = if (youTubeQueueTracks.isNotEmpty()) youTubeQueueIndex.coerceAtLeast(0) else currentIndex
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playback state: ${e.message}")
        }
    }
    
    fun play(track: Track) {
        // Validate track before playing
        if (track.title.isBlank() || track.contentUri.toString().isBlank()) {
            Log.e(TAG, "play() called with invalid track: title='${track.title}', uri='${track.contentUri}'")
            _error.value = "Cannot play: invalid track data"
            return
        }
        Log.d(TAG, "play() called for: ${track.title}")
        Log.d(TAG, "Track URI: ${track.contentUri}")
        setQueue(listOf(track), 0)
    }
    
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        Log.d(TAG, "setQueue() called with ${tracks.size} tracks, startIndex: $startIndex")
        
        if (!isControllerReady || mediaController == null) {
            Log.w(TAG, "MediaController not ready, waiting...")
            // Wait for controller to be ready
            scope.launch {
                var attempts = 0
                while (!isControllerReady && attempts < 20) {
                    delay(100)
                    attempts++
                }
                if (isControllerReady) {
                    Log.d(TAG, "Controller now ready, setting queue")
                    setQueueInternal(tracks, startIndex)
                } else {
                    Log.e(TAG, "Controller not ready after waiting")
                    _error.value = "Player not ready"
                }
            }
            return
        }
        
        setQueueInternal(tracks, startIndex)
    }
    
    private fun setQueueInternal(tracks: List<Track>, startIndex: Int) {
        currentQueue = tracks
        
        // Save the starting track to recently played
        if (startIndex >= 0 && startIndex < tracks.size) {
            val track = tracks[startIndex]
            recentlyPlayedManager.addSong(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                thumbnailUri = track.artworkUri,
                duration = track.duration
            )
        }
        
        val mediaItems = tracks.map { track ->
            Log.d(TAG, "Creating MediaItem for: ${track.title}")
            Log.d(TAG, "URI: ${track.contentUri}")
            createMediaItemForTrack(track)
        }
        
        mediaController?.apply {
            Log.d(TAG, "Setting media items and starting playback...")
            stop()
            clearMediaItems()
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            playWhenReady = true
            play()
            Log.d(TAG, "Playback started")
        }
    }
    
    fun togglePlayPause() {
        Log.d(TAG, "togglePlayPause() called")
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                Log.d(TAG, "Pausing...")
                controller.pause()
            } else {
                Log.d(TAG, "Playing...")
                controller.play()
            }
        } ?: Log.w(TAG, "mediaController is null")
    }
    
    fun pause() {
        mediaController?.pause()
    }
    
    fun resume() {
        mediaController?.play()
    }
    
    fun seekToQueueIndex(index: Int) {
        Log.d(TAG, "seekToQueueIndex($index)")
        if (playlistQueue.isNotEmpty()) {
            if (index in playlistQueue.indices) {
                playPlaylistQueueItem(index)
            }
        } else if (youTubeQueue.isNotEmpty()) {
            if (index in youTubeQueue.indices) {
                // Determine direction for optimization
                if (index == youTubeQueueIndex + 1) {
                    next() // Use optimized next() if applicable
                } else if (index == youTubeQueueIndex - 1) {
                    previous() // Use optimized previous() if applicable
                } else {
                    playYouTubeQueueItem(index)
                }
            }
        } else {
            mediaController?.seekToDefaultPosition(index)
        }
    }
    
    /**
     * INSTANT NEXT: Uses pre-prepared MediaItems for zero-delay transitions
     */
    fun next() {
        // Priority 1: Playlist Queue
        if (playlistQueue.isNotEmpty() && playlistQueueIndex >= 0) {
            val nextIndex = playlistQueueIndex + 1
            if (nextIndex < playlistQueue.size) {
                playPlaylistQueueItem(nextIndex)
                return
            }
        }
        
        // Priority 2: YouTube Search Queue with INSTANT playback
        if (youTubeQueue.isNotEmpty() && youTubeQueueIndex >= 0) {
            val nextIndex = youTubeQueueIndex + 1
            if (nextIndex < youTubeQueue.size) {
                val nextResult = youTubeQueue[nextIndex]
                
                // CHECK: Is next item already in ExoPlayer queue? Use seekToNext for instant transition
                val controller = mediaController
                if (controller != null && controller.mediaItemCount > 1 && controller.currentMediaItemIndex < controller.mediaItemCount - 1) {
                    Log.d(TAG, "⚡ INSTANT NEXT: Using ExoPlayer queue - zero delay!")
                    youTubeQueueIndex = nextIndex
                    controller.seekToNext()
                    
                    // Update current track info
                    val preparedTrack = preparedTracks[nextResult.videoId]
                    if (preparedTrack != null) {
                        currentQueue = listOf(preparedTrack)
                        // Update the YouTube queue stub with resolved track data
                        if (nextIndex in youTubeQueueTracks.indices) {
                            youTubeQueueTracks[nextIndex] = preparedTrack
                        }
                        recentlyPlayedManager.addSong(
                            id = preparedTrack.id,
                            title = preparedTrack.title,
                            artist = preparedTrack.artist,
                            album = preparedTrack.album,
                            thumbnailUri = preparedTrack.artworkUri,
                            duration = preparedTrack.duration
                        )
                    }
                    
                    // Keep prefetching ahead
                    prefetchAdjacentYouTubeItems(nextIndex)
                    return
                }
                
                // Fallback: Play directly (still fast if pre-prepared)
                playYouTubeQueueItem(nextIndex)
                return
            }
        }
        mediaController?.seekToNext()
    }
    
    /**
     * INSTANT PREVIOUS: Uses pre-prepared MediaItems for zero-delay transitions
     */
    fun previous() {
        // Priority 1: Playlist Queue
        if (playlistQueue.isNotEmpty() && playlistQueueIndex >= 0) {
            val prevIndex = playlistQueueIndex - 1
            if (prevIndex >= 0) {
                playPlaylistQueueItem(prevIndex)
                return
            }
        }
    
        // Priority 2: YouTube Search Queue with INSTANT playback
        if (youTubeQueue.isNotEmpty() && youTubeQueueIndex >= 0) {
            val prevIndex = youTubeQueueIndex - 1
            if (prevIndex >= 0) {
                val prevResult = youTubeQueue[prevIndex]
                
                // CHECK: Is previous item pre-prepared? Use it for instant playback
                val preparedItem = preparedMediaItems[prevResult.videoId]
                val preparedTrack = preparedTracks[prevResult.videoId]
                
                if (preparedItem != null && preparedTrack != null) {
                    Log.d(TAG, "⚡ INSTANT PREVIOUS: Using pre-prepared MediaItem - zero delay!")
                    youTubeQueueIndex = prevIndex
                    currentQueue = listOf(preparedTrack)
                    // Update the YouTube queue stub with resolved track data
                    if (prevIndex in youTubeQueueTracks.indices) {
                        youTubeQueueTracks[prevIndex] = preparedTrack
                    }
                    
                    recentlyPlayedManager.addSong(
                        id = preparedTrack.id,
                        title = preparedTrack.title,
                        artist = preparedTrack.artist,
                        album = preparedTrack.album,
                        thumbnailUri = preparedTrack.artworkUri,
                        duration = preparedTrack.duration
                    )
                    
                    // Build gapless queue: prev + current + next (if available)
                    val queueItems = mutableListOf(preparedItem)
                    
                    // Add current (now prev+1) and next items
                    for (i in 1..2) {
                        val idx = prevIndex + i
                        if (idx < youTubeQueue.size) {
                            val item = youTubeQueue[idx]
                            preparedMediaItems[item.videoId]?.let { queueItems.add(it) }
                        }
                    }
                    
                    mediaController?.apply {
                        stop()
                        clearMediaItems()
                        setMediaItems(queueItems, 0, 0L)
                        prepare()
                        playWhenReady = true
                        play()
                    }
                    
                    prefetchAdjacentYouTubeItems(prevIndex)
                    return
                }
                
                // Fallback: Play directly
                playYouTubeQueueItem(prevIndex)
                return
             }
        }
        mediaController?.seekToPrevious()
    }
    
    /**
     * Set the YouTube search results queue for navigation
     */
    fun setYouTubeQueue(results: List<YouTubeSearchResult>, currentIndex: Int) {
        youTubeQueue = results
        youTubeQueueIndex = currentIndex
        
        // Build Track stubs with thumbnails for carousel prev/next art display
        youTubeQueueTracks = results.map { result ->
            Track(
                id = "yt_${result.videoId}",
                title = result.title,
                artist = result.artist,
                album = "YouTube",
                duration = result.duration,
                artworkUri = Uri.parse(result.thumbnailUrl),
                contentUri = Uri.parse("https://www.youtube.com/watch?v=${result.videoId}"),
                source = com.audioflow.player.model.TrackSource.YOUTUBE
            )
        }.toMutableList()
        
        Log.d(TAG, "YouTube queue set with ${results.size} items (with thumbnails), starting at index $currentIndex")
    }
    
    /**
     * Clear the YouTube queue (when switching to local playback)
     */
    fun clearYouTubeQueue() {
        youTubeQueue = emptyList()
        youTubeQueueIndex = -1
        youTubeQueueTracks.clear()
    }
    
    fun clearPlaylistQueue() {
        playlistQueue = emptyList()
        playlistQueueIndex = -1
    }
    
    /**
     * Play a specific item from the YouTube queue with on-demand stream extraction
     * OPTIMIZED: Checks cache first for instant playback
     */
    private fun playYouTubeQueueItem(index: Int) {
        clearPlaylistQueue() // Ensure mutually exclusive
        if (index < 0 || index >= youTubeQueue.size || isExtractingStream) return
        
        val result = youTubeQueue[index]
        Log.d(TAG, "Playing YouTube queue item at index $index: ${result.title}")
        
        youTubeQueueIndex = index
        
        // CHECK CACHE FIRST for instant playback
        val cached = streamUrlCache[result.videoId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
            Log.d(TAG, "Using CACHED stream URL for ${result.title} (instant playback!)")
            playCachedYouTubeItem(result, cached)
            // Prefetch next items
            prefetchAdjacentYouTubeItems(index)
            return
        }
        
        // No cache - extract fresh URL
        isExtractingStream = true
        
        scope.launch {
            mediaRepository.getYouTubeStreamUrl(result.videoId)
                .onSuccess { streamInfo ->
                    // Cache the URL for future use
                    streamUrlCache[result.videoId] = CachedStreamUrl(
                        url = streamInfo.audioStreamUrl,
                        timestamp = System.currentTimeMillis(),
                        mimeType = streamInfo.mimeType
                    )
                    
                    val track = mediaRepository.createTrackFromYouTube(streamInfo)
                    currentQueue = listOf(track)
                    // Update the YouTube queue stub with resolved track data
                    if (index in youTubeQueueTracks.indices) {
                        youTubeQueueTracks[index] = track
                    }
                    
                    // Save to recently played
                    recentlyPlayedManager.addSong(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        thumbnailUri = track.artworkUri,
                        duration = track.duration
                    )
                    
                    val mediaItem = createMediaItemForTrack(track)
                    
                    mediaController?.apply {
                        stop()
                        clearMediaItems()
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                        play()
                    }
                    isExtractingStream = false
                    
                    // Prefetch adjacent items for next/prev
                    prefetchAdjacentYouTubeItems(index)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to extract YouTube stream: ${error.message}")
                    _error.value = "Failed to play: ${error.message}"
                    isExtractingStream = false
                }
        }
    }
    
    /**
     * GAPLESS PLAYBACK: Play a YouTube item from cached data with instant transition
     */
    private fun playCachedYouTubeItem(result: YouTubeSearchResult, cached: CachedStreamUrl) {
        val track = Track(
            id = "yt_${result.videoId}",
            title = result.title,
            artist = result.artist,
            album = "YouTube",
            duration = result.duration,
            artworkUri = Uri.parse(result.thumbnailUrl),
            contentUri = Uri.parse(cached.url),
            source = com.audioflow.player.model.TrackSource.YOUTUBE
        )
        currentQueue = listOf(track)
        // Update the YouTube queue stub with resolved track data
        if (youTubeQueueIndex in youTubeQueueTracks.indices) {
            youTubeQueueTracks[youTubeQueueIndex] = track
        }
        
        // Save to recently played
        recentlyPlayedManager.addSong(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            thumbnailUri = track.artworkUri,
            duration = track.duration
        )
        
        // Cache prepared track for future instant access
        preparedTracks[result.videoId] = track
        
        val mediaItem = createMediaItemForTrack(track)
        preparedMediaItems[result.videoId] = mediaItem
        
        // Build gapless queue with current + next items ready
        val queueItems = mutableListOf(mediaItem)
        
        // Add next 2 pre-prepared items to ExoPlayer queue for gapless playback
        for (i in 1..2) {
            val nextIdx = youTubeQueueIndex + i
            if (nextIdx < youTubeQueue.size) {
                val nextResult = youTubeQueue[nextIdx]
                val nextPrepared = preparedMediaItems[nextResult.videoId]
                if (nextPrepared != null) {
                    queueItems.add(nextPrepared)
                    Log.d(TAG, "GAPLESS: Added pre-prepared item $i to queue: ${nextResult.title}")
                }
            }
        }
        
        mediaController?.apply {
            stop()
            clearMediaItems()
            setMediaItems(queueItems, 0, 0L)
            prepare()
            playWhenReady = true
            play()
        }
    }
    
    /**
     * AGGRESSIVE PREFETCH: Prefetch 10 songs ahead and 3 behind for buttery smooth navigation
     */
    private fun prefetchAdjacentYouTubeItems(currentIndex: Int) {
        Log.d(TAG, "Starting aggressive prefetch from index $currentIndex")
        
        // Prefetch next 10 items for smooth forward navigation
        for (i in 1..10) {
            val nextIndex = currentIndex + i
            if (nextIndex < youTubeQueue.size) {
                prefetchYouTubeItemAt(nextIndex)
            }
        }
        
        // Prefetch previous 5 items for smooth backward navigation
        for (i in 1..5) {
            val prevIndex = currentIndex - i
            if (prevIndex >= 0) {
                prefetchYouTubeItemAt(prevIndex)
            }
        }
    }
    
    /**
     * Prefetch a specific YouTube queue item and PRE-BUILD Track + MediaItem
     */
    private fun prefetchYouTubeItemAt(index: Int) {
        if (index < 0 || index >= youTubeQueue.size) return
        
        val item = youTubeQueue[index]
        
        // Check if already fully prepared (has MediaItem ready)
        if (preparedMediaItems.containsKey(item.videoId)) {
            Log.d(TAG, "Already prepared: ${item.title}")
            return
        }
        
        // Check if URL is cached
        val cached = streamUrlCache[item.videoId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
            // URL is cached, now build Track + MediaItem if not already
            if (!preparedTracks.containsKey(item.videoId)) {
                val track = Track(
                    id = "yt_${item.videoId}",
                    title = item.title,
                    artist = item.artist,
                    album = "YouTube",
                    duration = item.duration,
                    artworkUri = Uri.parse(item.thumbnailUrl),
                    contentUri = Uri.parse(cached.url),
                    source = com.audioflow.player.model.TrackSource.YOUTUBE
                )
                preparedTracks[item.videoId] = track
                preparedMediaItems[item.videoId] = createMediaItemForTrack(track)
                Log.d(TAG, "INSTANT READY: Pre-built MediaItem for ${item.title}")
            }
            return
        }
        
        // Need to fetch URL first
        Log.d(TAG, "Prefetching YouTube item at $index: ${item.title}")
        scope.launch {
            mediaRepository.getYouTubeStreamUrl(item.videoId)
                .onSuccess { streamInfo ->
                    // Cache URL
                    streamUrlCache[item.videoId] = CachedStreamUrl(
                        url = streamInfo.audioStreamUrl,
                        timestamp = System.currentTimeMillis(),
                        mimeType = streamInfo.mimeType
                    )
                    
                    // IMMEDIATELY build Track + MediaItem for instant playback
                    val track = Track(
                        id = "yt_${item.videoId}",
                        title = item.title,
                        artist = item.artist,
                        album = "YouTube",
                        duration = item.duration,
                        artworkUri = Uri.parse(item.thumbnailUrl),
                        contentUri = Uri.parse(streamInfo.audioStreamUrl),
                        source = com.audioflow.player.model.TrackSource.YOUTUBE
                    )
                    preparedTracks[item.videoId] = track
                    preparedMediaItems[item.videoId] = createMediaItemForTrack(track)
                    
                    Log.d(TAG, "✓ FULLY PREPARED: ${item.title} ready for instant playback")
                }
                .onFailure { error ->
                    Log.w(TAG, "Prefetch failed for ${item.title}: ${error.message}")
                }
        }
    }
    
    /**
     * Prefetch the next YouTube queue item's stream URL for seamless playback
     */
    private fun prefetchNextYouTubeQueueItem(currentIndex: Int) {
        val nextIndex = currentIndex + 1
        if (nextIndex >= youTubeQueue.size) return
        
        val nextResult = youTubeQueue[nextIndex]
        val cached = streamUrlCache[nextResult.videoId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
            return // Already cached
        }
        
        Log.d(TAG, "Prefetching next YouTube queue item: ${nextResult.title}")
        scope.launch {
            mediaRepository.getYouTubeStreamUrl(nextResult.videoId)
                .onSuccess { streamInfo ->
                    streamUrlCache[nextResult.videoId] = CachedStreamUrl(
                        url = streamInfo.audioStreamUrl,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Prefetched stream URL for ${nextResult.title}")
                }
                .onFailure { error ->
                    Log.w(TAG, "Prefetch failed for ${nextResult.title}: ${error.message}")
                }
        }
    }
    
    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }
    
    fun seekToProgress(progress: Float) {
        val duration = mediaController?.duration ?: return
        if (duration > 0) {
            seekTo((progress * duration).toLong())
        }
    }
    
    fun toggleShuffle() {
        mediaController?.let { controller ->
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        }
    }
    
    fun cycleRepeatMode() {
        mediaController?.let { controller ->
            controller.repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    
    fun addToQueue(track: Track) {
        if (!isControllerReady || mediaController == null) return
        
        scope.launch {
            val mediaItem = createMediaItemForTrack(track)
            mediaController?.addMediaItem(mediaItem)
            
            // Update local queue state
            currentQueue = currentQueue + track
            updatePlaybackState()
        }
    }
    
    fun addNext(track: Track) {
        if (!isControllerReady || mediaController == null) {
            // Fallback if not ready: just play it now or add to queue
            addToQueue(track)
            return
        }
        
        scope.launch {
            val controller = mediaController ?: return@launch
            val nextIndex = controller.currentMediaItemIndex + 1
            val mediaItem = createMediaItemForTrack(track)
            
            if (nextIndex <= controller.mediaItemCount) {
                controller.addMediaItem(nextIndex, mediaItem)
                
                // Update local queue state
                val mutableQueue = currentQueue.toMutableList()
                if (nextIndex <= mutableQueue.size) {
                    mutableQueue.add(nextIndex, track)
                    currentQueue = mutableQueue
                } else {
                    currentQueue = currentQueue + track
                }
            } else {
                controller.addMediaItem(mediaItem)
                currentQueue = currentQueue + track
            }
            updatePlaybackState()
            Log.d(TAG, "Added to queue next: ${track.title}")
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controllerFuture = null
        mediaController = null
        isControllerReady = false
    }
}
