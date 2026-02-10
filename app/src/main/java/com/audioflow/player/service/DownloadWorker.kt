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
            Log.d(TAG, "=== DOWNLOAD START for: $trackId ===")
            
            // Update status to DOWNLOADING
            updateProgress(trackId, 5, "Preparing...")
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
            
            Log.d(TAG, "yt-dlp is ready")

            // Setup download directory
            val downloadDir = File(applicationContext.filesDir, "downloads")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            
            val videoId = trackId.removePrefix("yt_")
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            
            // Clean up any previous partial downloads for this video
            downloadDir.listFiles()?.filter { it.name.startsWith(videoId) }?.forEach { 
                it.delete()
                Log.d(TAG, "Cleaned up old file: ${it.name}")
            }
            
            // Build yt-dlp request - SIMPLE and RELIABLE
            val request = YoutubeDLRequest(videoUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("--no-check-certificate")
            
            // PERMISSIVE format: try many fallbacks to ensure something downloads
            // Android client has limited formats, so use broad selectors
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best[ext=m4a]/best")
            
            // DON'T use -x or --audio-format (requires ffmpeg processing = slow/hangs)
            // Just download the raw audio stream directly
            
            // Use default player client (web has more formats than android)
            // Don't restrict to android client since it limits available formats
            request.addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            
            // Output to specific file
            val outputPath = "${downloadDir.absolutePath}/${videoId}.%(ext)s"
            request.addOption("-o", outputPath)
            
            // Timeout and retries
            request.addOption("--socket-timeout", "15")
            request.addOption("--retries", "2")
            request.addOption("--fragment-retries", "2")
            
            // Throttle protection
            request.addOption("--throttled-rate", "100K")
            
            updateProgress(trackId, 10, "Downloading...")
            Log.d(TAG, "Executing yt-dlp download for: $videoUrl")
            val startTime = System.currentTimeMillis()
            
            // Execute download - NO progress callback to avoid deadlock
            // (runBlocking inside withContext(Dispatchers.IO) = deadlock!)
            val response = YoutubeDL.getInstance().execute(request)
            
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            Log.d(TAG, "yt-dlp finished in ${elapsed}s, exit code: ${response.exitCode}")
            Log.d(TAG, "yt-dlp stdout: ${response.out?.take(500)}")
            
            if (response.exitCode != 0) {
                val errorMsg = response.err?.take(300) ?: "Unknown download error"
                Log.e(TAG, "yt-dlp stderr: $errorMsg")
                throw Exception("Download failed (exit ${response.exitCode}): $errorMsg")
            }
            
            // Find the downloaded file
            val downloadedFile = downloadDir.listFiles()
                ?.filter { it.name.startsWith(videoId) && it.length() > 0 }
                ?.maxByOrNull { it.length() } // Pick largest if multiple
            
            if (downloadedFile == null || !downloadedFile.exists()) {
                // List all files for debugging
                val files = downloadDir.listFiles()?.map { "${it.name} (${it.length()})" }
                Log.e(TAG, "No file found! Files in dir: $files")
                throw Exception("Download produced no file")
            }
            
            if (downloadedFile.length() == 0L) {
                downloadedFile.delete()
                throw Exception("Download produced empty file")
            }
            
            Log.d(TAG, "Downloaded: ${downloadedFile.name} (${downloadedFile.length() / 1024} KB)")

            // Update DB with path and COMPLETED status
            val entity = downloadedSongDao.getDownloadedSong(trackId)
            if (entity != null) {
                downloadedSongDao.insert(entity.copy(
                    localPath = downloadedFile.absolutePath,
                    status = DownloadStatus.COMPLETED
                ))
                Log.d(TAG, "DB updated with path: ${downloadedFile.absolutePath}")
            } else {
                Log.e(TAG, "Entity not found in DB for: $trackId")
            }
            
            updateProgress(trackId, 100, "Complete")
            Log.d(TAG, "=== DOWNLOAD COMPLETE: $trackId (${downloadedFile.length() / 1024} KB in ${elapsed}s) ===")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "=== DOWNLOAD FAILED for $trackId: ${e.message} ===", e)
            downloadedSongDao.updateStatus(trackId, DownloadStatus.FAILED.name)
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    /**
     * Update download progress for UI
     */
    private suspend fun updateProgress(trackId: String, progress: Int, message: String) {
        try {
            setProgress(workDataOf(
                "trackId" to trackId,
                "progress" to progress,
                "message" to message
            ))
        } catch (e: Exception) {
            // Ignore progress update failures - don't let them crash the download
            Log.w(TAG, "Progress update failed: ${e.message}")
        }
    }
}
