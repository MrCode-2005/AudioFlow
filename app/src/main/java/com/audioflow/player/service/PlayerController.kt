package com.audioflow.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private var currentQueue: List<Track> = emptyList()
    
    init {
        // Delay initialization slightly to ensure app is fully ready
        scope.launch {
            delay(500)
            initializeController()
        }
    }
    
    private fun initializeController() {
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, MusicService::class.java)
            )
            
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    mediaController = controllerFuture?.get()
                    setupPlayerListener()
                    startPositionUpdates()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updatePlaybackState()
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
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
                delay(1000)
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
        setQueue(listOf(track), 0)
    }
    
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        currentQueue = tracks
        
        val mediaItems = tracks.map { track ->
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
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            play()
        }
    }
    
    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
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
    
    fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controllerFuture = null
        mediaController = null
    }
}
