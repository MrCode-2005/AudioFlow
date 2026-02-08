package com.audioflow.player.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.audioflow.player.data.local.dao.DownloadedSongDao
import com.audioflow.player.data.local.entity.DownloadStatus
import com.audioflow.player.data.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadedSongDao: DownloadedSongDao,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString("trackId") ?: return@withContext Result.failure()
        
        try {
            // Update status to DOWNLOADING (idempotent)
            downloadedSongDao.updateStatus(trackId, DownloadStatus.DOWNLOADING.name)

            // 1. Get Stream URL (from YouTube)
            // trackId is "yt_VIDEOID" usually. Remove prefix.
            val videoId = trackId.removePrefix("yt_")
            
            val streamInfo = mediaRepository.getYouTubeStreamUrl(videoId).getOrThrow()
            val downloadUrl = streamInfo.audioStreamUrl

            // 2. Download File
            val fileName = "song_${trackId}.m4a" // Assuming m4a/mp4
            val file = File(applicationContext.filesDir, fileName)
            
            URL(downloadUrl).openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            // 3. Update DB with path and COMPLETED status
            val entity = downloadedSongDao.getDownloadedSong(trackId)
            if (entity != null) {
                downloadedSongDao.insert(entity.copy(
                    localPath = file.absolutePath,
                    status = DownloadStatus.COMPLETED
                ))
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            downloadedSongDao.updateStatus(trackId, DownloadStatus.FAILED.name)
            Result.failure()
        }
    }
}
