package com.audioflow.player.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.audioflow.player.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val TAG = "MusicService"

@AndroidEntryPoint
class MusicService : MediaSessionService() {
    
    @javax.inject.Inject
    lateinit var cookieManager: com.audioflow.player.data.remote.YouTubeCookieManager
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService onCreate")
        
        // Use OkHttp for reliable streaming on physical devices
        // Use Android YouTube app User-Agent to match yt-dlp extraction (player_client=android)
        val androidUserAgent = "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip"
        
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
            
        // Create OkHttp data source factory with minimal headers
        // Android client URLs don't need Origin/Referer like web URLs
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(androidUserAgent)
        
        // Wrap with DefaultDataSource for local file support
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        
        // Create media source factory with OkHttp
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        // Create ExoPlayer with custom media source factory and audio track selection
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // Handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                // Ensure audio volume is at max
                volume = 1.0f
                
                // Add comprehensive error and state listener for debugging
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "=== EXOPLAYER ERROR ===")
                        Log.e(TAG, "Error code: ${error.errorCode}")
                        Log.e(TAG, "Error message: ${error.message}")
                        Log.e(TAG, "Error cause: ${error.cause?.message}")
                        error.cause?.printStackTrace()
                    }
                    
                    override fun onPlayerErrorChanged(error: PlaybackException?) {
                        if (error != null) {
                            Log.e(TAG, "Player error changed: ${error.errorCode} - ${error.message}")
                        }
                    }
                    
                    override fun onPlaybackStateChanged(state: Int) {
                        val stateName = when (state) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        }
                        Log.d(TAG, "=== ExoPlayer state: $stateName ===")
                        
                        // Log additional info when buffering
                        if (state == Player.STATE_BUFFERING) {
                            Log.d(TAG, "Buffering... duration: $duration, position: $currentPosition")
                        }
                        if (state == Player.STATE_READY) {
                            Log.d(TAG, "Ready to play! duration: $duration, playWhenReady: $playWhenReady")
                        }
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "isPlaying changed to: $isPlaying")
                    }
                    
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        Log.d(TAG, "Media transition: ${mediaItem?.mediaId}, reason: $reason")
                        mediaItem?.localConfiguration?.let { config ->
                            Log.d(TAG, "URI: ${config.uri?.toString()?.take(100)}...")
                            Log.d(TAG, "MIME type: ${config.mimeType}")
                        }
                    }
                })
            }
        
        Log.d(TAG, "ExoPlayer created successfully with audio focus and error listener")
        
        // Create pending intent for launching app from notification
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Create media session
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivity)
            .setCallback(MediaSessionCallback())
            .build()
        
        Log.d(TAG, "MediaSession created successfully")
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "MusicService onDestroy")
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
    
    private inner class MediaSessionCallback : MediaSession.Callback {
        // Default implementations handle play, pause, seek, etc.
    }
}
