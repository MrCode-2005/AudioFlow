package com.audioflow.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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

private const val TAG = "PlayerController"

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private var currentQueue: List<Track> = emptyList()
    private var isControllerReady = false
    
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
                updatePlaybackState()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}")
                Log.e(TAG, "Error code: ${error.errorCode}")
                _error.value = error.message ?: "Playback error"
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
                delay(500) // Update more frequently for smoother progress
            }
        }
    }
    
    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        
        _playbackState.value = PlaybackState(
            isPlaying = controller.isPlaying,
            currentTrack = if (currentIndex >= 0 && currentIndex < currentQueue.size) {
                currentQueue[currentIndex]
            } else null,
            currentPosition = controller.currentPosition,
            duration = controller.duration.takeIf { it > 0 } ?: 0,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            queue = currentQueue,
            currentQueueIndex = currentIndex
        )
    }
    
    fun play(track: Track) {
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
        
        val mediaItems = tracks.map { track ->
            Log.d(TAG, "Creating MediaItem for: ${track.title}")
            Log.d(TAG, "URI: ${track.contentUri}")
            
            MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artworkUri)
                        .build()
                )
                .build()
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
    
    fun next() {
        mediaController?.seekToNext()
    }
    
    fun previous() {
        mediaController?.seekToPrevious()
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
        currentQueue = currentQueue + track
        
        val mediaItem = MediaItem.Builder()
            .setUri(track.contentUri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri)
                    .build()
            )
            .build()
        
        mediaController?.addMediaItem(mediaItem)
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
