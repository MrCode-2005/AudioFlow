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

// MUST match the User-Agent used in YouTubeExtractor for URL extraction
// YouTube binds URLs to the User-Agent that extracted them
private const val ANDROID_YT_USER_AGENT = "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip"

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadedSongDao: DownloadedSongDao,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(appContext, workerParams) {

    // OkHttp client for downloading - uses same UA as extraction
    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString("trackId") ?: return@withContext Result.failure()
        
        try {
            Log.d(TAG, "=== DOWNLOAD START: $trackId ===")
            
            updateProgress(trackId, 5, "Preparing...")
            downloadedSongDao.updateStatus(trackId, DownloadStatus.DOWNLOADING.name)

            // Step 1: Extract stream URL using the SAME method as playback
            // This works reliably since playback already works!
            val videoId = trackId.removePrefix("yt_")
            updateProgress(trackId, 10, "Getting audio URL...")
            Log.d(TAG, "Extracting URL for: $videoId")
            
            val streamInfo = mediaRepository.getYouTubeStreamUrl(videoId).getOrElse { error ->
                Log.e(TAG, "URL extraction failed: ${error.message}")
                throw Exception("Could not get audio URL: ${error.message}")
            }
            
            val downloadUrl = streamInfo.audioStreamUrl
            if (downloadUrl.isBlank()) {
                throw Exception("Empty audio URL")
            }
            
            Log.d(TAG, "Got URL (${downloadUrl.length} chars): ${downloadUrl.take(100)}...")
            updateProgress(trackId, 20, "Downloading audio...")
            
            // Step 2: Download using OkHttp with SAME User-Agent as extraction
            // This is critical - YouTube URLs are bound to the UA that extracted them
            val downloadDir = File(applicationContext.filesDir, "downloads")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            
            val fileName = "${videoId}.m4a"
            val file = File(downloadDir, fileName)
            
            // Clean up any previous partial download
            if (file.exists()) file.delete()
            
            downloadWithOkHttp(downloadUrl, file, trackId)
            
            // Step 3: Verify file
            if (!file.exists() || file.length() < 1024) { // At least 1KB
                file.delete()
                throw Exception("Download produced invalid file (${file.length()} bytes)")
            }
            
            Log.d(TAG, "File downloaded: ${file.name} (${file.length() / 1024} KB)")

            // Step 4: Update DB
            val entity = downloadedSongDao.getDownloadedSong(trackId)
            if (entity != null) {
                downloadedSongDao.insert(entity.copy(
                    localPath = file.absolutePath,
                    status = DownloadStatus.COMPLETED
                ))
            }
            
            updateProgress(trackId, 100, "Complete")
            Log.d(TAG, "=== DOWNLOAD COMPLETE: $trackId (${file.length() / 1024} KB) ===")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "=== DOWNLOAD FAILED: $trackId - ${e.message} ===", e)
            downloadedSongDao.updateStatus(trackId, DownloadStatus.FAILED.name)
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    /**
     * Download file using OkHttp with the SAME User-Agent used for extraction.
     * YouTube URLs are bound to the User-Agent, so mismatching = 403 error.
     */
    private fun downloadWithOkHttp(url: String, file: File, trackId: String) {
        Log.d(TAG, "Starting OkHttp download...")
        val startTime = System.currentTimeMillis()
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", ANDROID_YT_USER_AGENT) // MUST match extraction UA
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity") // No compression for audio binary
            .header("Connection", "keep-alive")
            .build()
        
        val response = downloadClient.newCall(request).execute()
        
        Log.d(TAG, "Response: ${response.code} ${response.message}")
        
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        
        val body = response.body
        if (body == null) {
            response.close()
            throw Exception("Empty response body")
        }
        
        val contentLength = body.contentLength()
        Log.d(TAG, "Content-Length: $contentLength bytes (${contentLength / 1024} KB)")
        
        var downloadedBytes = 0L
        var lastLogTime = System.currentTimeMillis()
        
        try {
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(32768) // 32KB buffer for speed
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Log progress every second
                        val now = System.currentTimeMillis()
                        if (now - lastLogTime > 1000) {
                            val progress = if (contentLength > 0) {
                                ((downloadedBytes * 80) / contentLength).toInt() + 20
                            } else {
                                ((downloadedBytes * 80) / (5 * 1024 * 1024)).coerceAtMost(80).toInt() + 20
                            }
                            val speedKBps = (downloadedBytes / 1024.0 / ((now - startTime) / 1000.0)).toInt()
                            Log.d(TAG, "Progress: ${downloadedBytes/1024}KB / ${contentLength/1024}KB ($speedKBps KB/s)")
                            lastLogTime = now
                        }
                    }
                    
                    output.flush()
                }
            }
        } finally {
            response.close()
        }
        
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        Log.d(TAG, "Download done: ${downloadedBytes / 1024} KB in ${elapsed}s")
    }
    
    private suspend fun updateProgress(trackId: String, progress: Int, message: String) {
        try {
            setProgress(workDataOf(
                "trackId" to trackId,
                "progress" to progress,
                "message" to message
            ))
        } catch (e: Exception) {
            Log.w(TAG, "Progress update failed: ${e.message}")
        }
    }
}
