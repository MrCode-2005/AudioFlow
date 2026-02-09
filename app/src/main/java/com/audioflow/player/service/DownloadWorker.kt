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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    // OkHttp client optimized for downloads with shorter timeouts
    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
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

            // 1. Get Stream URL with TIMEOUT (max 20 seconds for extraction)
            val downloadUrl = if (!preExtractedUrl.isNullOrBlank()) {
                Log.d(TAG, "Using pre-extracted URL")
                updateProgress(trackId, 10, "URL ready")
                preExtractedUrl
            } else {
                updateProgress(trackId, 5, "Extracting audio...")
                val videoId = trackId.removePrefix("yt_")
                
                // Add timeout for extraction - this is the slowest part
                val streamInfo = try {
                    withTimeout(20_000L) { // 20 second timeout
                        mediaRepository.getYouTubeStreamUrl(videoId).getOrThrow()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "URL extraction timed out for $videoId")
                    throw Exception("Extraction timed out - try again")
                }
                
                updateProgress(trackId, 20, "URL extracted")
                Log.d(TAG, "Extracted URL length: ${streamInfo.audioStreamUrl.length}")
                streamInfo.audioStreamUrl
            }
            
            if (downloadUrl.isBlank()) {
                throw Exception("Empty download URL")
            }

            // 2. Download File with Progress using OkHttp (with timeout)
            val fileName = "song_${trackId.replace(":", "_").replace("/", "_")}.m4a"
            val file = File(applicationContext.filesDir, fileName)
            
            updateProgress(trackId, 25, "Downloading...")
            
            try {
                withTimeout(60_000L) { // 60 second timeout for download
                    downloadWithProgress(downloadUrl, file, trackId)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Download timed out")
                file.delete() // Clean up partial file
                throw Exception("Download timed out - check your connection")
            }
            
            // Verify file was downloaded
            if (!file.exists() || file.length() == 0L) {
                throw Exception("Download failed - empty file")
            }

            // 3. Update DB with path and COMPLETED status
            val entity = downloadedSongDao.getDownloadedSong(trackId)
            if (entity != null) {
                downloadedSongDao.insert(entity.copy(
                    localPath = file.absolutePath,
                    status = DownloadStatus.COMPLETED
                ))
                Log.d(TAG, "Updated DB: ${file.absolutePath}, size: ${file.length()} bytes")
            } else {
                Log.e(TAG, "Entity not found in DB for: $trackId")
            }
            
            updateProgress(trackId, 100, "Complete")
            Log.d(TAG, "Download complete: $trackId (${file.length() / 1024} KB)")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $trackId: ${e.message}", e)
            downloadedSongDao.updateStatus(trackId, DownloadStatus.FAILED.name)
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    /**
     * Download file with progress tracking using OkHttp
     */
    private suspend fun downloadWithProgress(url: String, file: File, trackId: String) {
        Log.d(TAG, "Starting OkHttp download: ${url.take(80)}...")
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity") // Don't use compression for audio
            .build()
        
        val response = downloadClient.newCall(request).execute()
        
        Log.d(TAG, "Response code: ${response.code}")
        
        if (!response.isSuccessful) {
            response.close()
            throw Exception("Download failed with code: ${response.code}")
        }
        
        val body = response.body
        if (body == null) {
            response.close()
            throw Exception("Empty response body")
        }
        
        val contentLength = body.contentLength()
        Log.d(TAG, "Content-Length: $contentLength bytes (${contentLength / 1024} KB)")
        
        var downloadedBytes = 0L
        var lastProgressUpdate = System.currentTimeMillis()
        val startTime = System.currentTimeMillis()
        
        try {
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(16384) // 16KB buffer for faster downloads
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Update progress every 200ms
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 200) {
                            val progress = if (contentLength > 0) {
                                ((downloadedBytes * 75) / contentLength).toInt() + 25 // 25-100%
                            } else {
                                // Unknown size - estimate based on typical song size (~4MB)
                                ((downloadedBytes * 75) / (4 * 1024 * 1024)).coerceAtMost(75).toInt() + 25
                            }
                            
                            // Calculate speed
                            val elapsedSeconds = (now - startTime) / 1000.0
                            val speedKBps = if (elapsedSeconds > 0) {
                                (downloadedBytes / 1024.0 / elapsedSeconds).toInt()
                            } else 0
                            
                            // Calculate ETA
                            val remainingBytes = if (contentLength > 0) contentLength - downloadedBytes else 0
                            val etaSeconds = if (speedKBps > 0 && remainingBytes > 0) {
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
        } finally {
            response.close()
        }
        
        Log.d(TAG, "Download finished: ${downloadedBytes / 1024} KB in ${(System.currentTimeMillis() - startTime) / 1000}s")
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
