package com.audioflow.player.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
        
        // Create OkHttpClient with cookie interceptor for YouTube streams
        // Cookies are fetched dynamically per-request to ensure fresh auth
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val url = originalRequest.url.toString()
                
                // Add cookies only for YouTube/Google video URLs
                val newRequest = if (url.contains("googlevideo.com") || url.contains("youtube.com")) {
                    // Fetch fresh cookies for each request (important if user logs in after service starts)
                    val freshCookies = cookieManager.getCookies()
                    Log.d(TAG, "Sending request with cookies: ${if (freshCookies.isNotEmpty()) "present" else "empty"}")
                    originalRequest.newBuilder()
                        .header("Cookie", freshCookies)
                        .build()
                } else {
                    originalRequest
                }
                chain.proceed(newRequest)
            }
            .build()
        
        // Create OkHttp data source factory with web browser User-Agent (must match cookie authentication)
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(com.audioflow.player.data.remote.YouTubeCookieManager.USER_AGENT)
        
        // Wrap with DefaultDataSource to handle both HTTP and local content:// URIs
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        
        // Create media source factory with OkHttp data source
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)
        
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
            }
        
        Log.d(TAG, "ExoPlayer created successfully with audio focus")
        
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
