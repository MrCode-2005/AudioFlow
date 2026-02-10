package com.audioflow.player.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.audioflow.player.data.local.dao.DownloadedSongDao
import com.audioflow.player.data.local.entity.DownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DownloadWorker"

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadedSongDao: DownloadedSongDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString("trackId") ?: return@withContext Result.failure()
        
        try {
            Log.d(TAG, "Starting download for: $trackId")
            
            // Update status to DOWNLOADING
            updateProgress(trackId, 0, "Preparing...")
            downloadedSongDao.updateStatus(trackId, DownloadStatus.DOWNLOADING.name)

            // Wait for yt-dlp initialization
            var attempts = 0
            while (!com.audioflow.player.AudioFlowApp.isYtDlpReady && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            if (!com.audioflow.player.AudioFlowApp.isYtDlpReady) {
                throw Exception("yt-dlp not initialized")
            }

            // Setup download directory
            val downloadDir = File(applicationContext.filesDir, "downloads")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            
            val videoId = trackId.removePrefix("yt_")
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            
            // Use yt-dlp to download - it handles all YouTube throttling/signatures
            val request = YoutubeDLRequest(videoUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--no-check-certificate")
            
            // Download audio only, best quality m4a
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best")
            request.addOption("-x") // Extract audio
            request.addOption("--audio-format", "m4a") // Convert to m4a
            request.addOption("--audio-quality", "0") // Best quality
            
            // Use Android player client for reliable URLs
            request.addOption("--extractor-args", "youtube:player_client=android")
            request.addOption("--user-agent", "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip")
            
            // Output path
            val outputTemplate = "${downloadDir.absolutePath}/${videoId}.%(ext)s"
            request.addOption("-o", outputTemplate)
            
            // Socket timeout for faster failure
            request.addOption("--socket-timeout", "15")
            request.addOption("--retries", "3")
            
            updateProgress(trackId, 10, "Downloading...")
            Log.d(TAG, "Starting yt-dlp download for $videoId")
            
            // Execute download with progress callback
            val response = YoutubeDL.getInstance().execute(
                request
            ) { progress, etaInSeconds, _ ->
                // progress is 0-100 float
                val percent = progress.toInt().coerceIn(10, 99)
                val eta = if (etaInSeconds > 0) "${etaInSeconds}s left" else "Downloading..."
                val msg = "${percent}% • $eta"
                
                // Update progress on main scope (can't use suspend here)
                Log.d(TAG, "Download progress: $msg")
                
                // WorkManager progress update via runBlocking since callback isn't suspend
                kotlinx.coroutines.runBlocking {
                    updateProgress(trackId, percent, msg)
                }
            }
            
            Log.d(TAG, "yt-dlp exit code: ${response.exitCode}")
            Log.d(TAG, "yt-dlp output: ${response.out?.take(200)}")
            
            if (response.exitCode != 0) {
                val errorMsg = response.err?.take(200) ?: "Unknown error"
                Log.e(TAG, "yt-dlp error: $errorMsg")
                throw Exception("Download failed: $errorMsg")
            }
            
            // Find the downloaded file (may have .m4a or .webm extension)
            val downloadedFile = downloadDir.listFiles()?.find { 
                it.name.startsWith(videoId) && it.length() > 0 
            }
            
            if (downloadedFile == null || !downloadedFile.exists() || downloadedFile.length() == 0L) {
                throw Exception("Download failed - no file produced")
            }
            
            Log.d(TAG, "Downloaded file: ${downloadedFile.name} (${downloadedFile.length() / 1024} KB)")

            // Update DB with path and COMPLETED status
            val entity = downloadedSongDao.getDownloadedSong(trackId)
            if (entity != null) {
                downloadedSongDao.insert(entity.copy(
                    localPath = downloadedFile.absolutePath,
                    status = DownloadStatus.COMPLETED
                ))
                Log.d(TAG, "Updated DB: ${downloadedFile.absolutePath}")
            } else {
                Log.e(TAG, "Entity not found in DB for: $trackId")
            }
            
            updateProgress(trackId, 100, "Complete")
            Log.d(TAG, "Download complete: $trackId (${downloadedFile.length() / 1024} KB)")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $trackId: ${e.message}", e)
            downloadedSongDao.updateStatus(trackId, DownloadStatus.FAILED.name)
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    /**
     * Update download progress for UI
     */
    private suspend fun updateProgress(trackId: String, progress: Int, message: String) {
        Log.d(TAG, "Progress: $trackId - $progress% - $message")
        setProgress(workDataOf(
            "trackId" to trackId,
            "progress" to progress,
            "message" to message
        ))
    }
}
