package com.audioflow.player.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.audioflow.player.data.local.dao.DownloadedSongDao
import com.audioflow.player.data.local.entity.DownloadStatus
import com.audioflow.player.data.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "DownloadWorker"

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadedSongDao: DownloadedSongDao,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(appContext, workerParams) {

    // OkHttp client optimized for downloads
    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString("trackId") ?: return@withContext Result.failure()
        val preExtractedUrl = inputData.getString("streamUrl") // May already have URL
        
        try {
            Log.d(TAG, "Starting download for: $trackId")
            
            // Update status to DOWNLOADING
            updateProgress(trackId, 0, "Preparing...")
            downloadedSongDao.updateStatus(trackId, DownloadStatus.DOWNLOADING.name)

            // 1. Get Stream URL (skip if pre-extracted)
            val downloadUrl = if (!preExtractedUrl.isNullOrBlank()) {
                Log.d(TAG, "Using pre-extracted URL")
                updateProgress(trackId, 5, "URL ready")
                preExtractedUrl
            } else {
                updateProgress(trackId, 2, "Extracting audio...")
                val videoId = trackId.removePrefix("yt_")
                val streamInfo = mediaRepository.getYouTubeStreamUrl(videoId).getOrThrow()
                updateProgress(trackId, 10, "URL extracted")
                streamInfo.audioStreamUrl
            }

            // 2. Download File with Progress using OkHttp
            val fileName = "song_${trackId}.m4a"
            val file = File(applicationContext.filesDir, fileName)
            
            updateProgress(trackId, 15, "Downloading...")
            downloadWithProgress(downloadUrl, file, trackId)

            // 3. Update DB with path and COMPLETED status
            val entity = downloadedSongDao.getDownloadedSong(trackId)
            if (entity != null) {
                downloadedSongDao.insert(entity.copy(
                    localPath = file.absolutePath,
                    status = DownloadStatus.COMPLETED
                ))
            }
            
            updateProgress(trackId, 100, "Complete")
            Log.d(TAG, "Download complete: $trackId")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            downloadedSongDao.updateStatus(trackId, DownloadStatus.FAILED.name)
            Result.failure()
        }
    }
    
    /**
     * Download file with progress tracking using OkHttp
     */
    private suspend fun downloadWithProgress(url: String, file: File, trackId: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip")
            .header("Accept", "*/*")
            .build()
        
        val response = downloadClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Download failed with code: ${response.code}")
        }
        
        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()
        
        Log.d(TAG, "Content-Length: $contentLength bytes")
        
        var downloadedBytes = 0L
        var lastProgressUpdate = System.currentTimeMillis()
        val startTime = System.currentTimeMillis()
        
        body.byteStream().use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    // Update progress every 100ms to avoid too many updates
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 100) {
                        val progress = if (contentLength > 0) {
                            ((downloadedBytes * 85) / contentLength).toInt() + 15 // 15-100%
                        } else {
                            // Unknown size - estimate based on typical song size (~4MB)
                            ((downloadedBytes * 85) / (4 * 1024 * 1024)).coerceAtMost(85).toInt() + 15
                        }
                        
                        // Calculate speed
                        val elapsedSeconds = (now - startTime) / 1000.0
                        val speedKBps = if (elapsedSeconds > 0) {
                            (downloadedBytes / 1024.0 / elapsedSeconds).toInt()
                        } else 0
                        
                        // Calculate ETA
                        val remainingBytes = if (contentLength > 0) contentLength - downloadedBytes else 0
                        val etaSeconds = if (speedKBps > 0 && contentLength > 0) {
                            (remainingBytes / 1024 / speedKBps).toInt()
                        } else 0
                        
                        val progressText = if (etaSeconds > 0) {
                            "${progress}% • ${speedKBps} KB/s • ${etaSeconds}s"
                        } else {
                            "${progress}% • ${speedKBps} KB/s"
                        }
                        
                        updateProgress(trackId, progress.coerceAtMost(99), progressText)
                        lastProgressUpdate = now
                    }
                }
            }
        }
        
        response.close()
        Log.d(TAG, "Download complete: ${downloadedBytes / 1024} KB")
    }
    
    /**
     * Update download progress for UI
     */
    private suspend fun updateProgress(trackId: String, progress: Int, message: String) {
        // Use WorkManager's setProgress for real-time updates
        setProgress(workDataOf(
            "trackId" to trackId,
            "progress" to progress,
            "message" to message
        ))
    }
}
