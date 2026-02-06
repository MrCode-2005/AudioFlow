package com.audioflow.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AudioFlowApp"

@HiltAndroidApp
class AudioFlowApp : Application() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "audioflow_playback"
        
        @Volatile
        var isYtDlpReady = false
            private set
    }
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeYtDlpAsync()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Initialize yt-dlp and FFmpeg asynchronously to avoid blocking app startup.
     * YouTube features will be available once initialization completes.
     */
    private fun initializeYtDlpAsync() {
        applicationScope.launch {
            try {
                Log.d(TAG, "Initializing yt-dlp...")
                YoutubeDL.getInstance().init(this@AudioFlowApp)
                FFmpeg.getInstance().init(this@AudioFlowApp)
                isYtDlpReady = true
                Log.d(TAG, "yt-dlp and FFmpeg initialized successfully")
                
                // Update yt-dlp binary in background to handle YouTube changes
                try {
                    val status = YoutubeDL.getInstance().updateYoutubeDL(
                        this@AudioFlowApp,
                        YoutubeDL.UpdateChannel.STABLE
                    )
                    Log.d(TAG, "yt-dlp update status: $status")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update yt-dlp (using bundled version): ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize yt-dlp", e)
            }
        }
    }
}
