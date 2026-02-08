package com.audioflow.player.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.audioflow.player.data.local.dao.DownloadedSongDao
import com.audioflow.player.data.local.entity.DownloadStatus
import com.audioflow.player.data.local.entity.DownloadedSongEntity
import com.audioflow.player.model.Track
import com.audioflow.player.service.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedSongDao: DownloadedSongDao
) {
    val allDownloads: Flow<List<DownloadedSongEntity>> = downloadedSongDao.getAllDownloadedSongs()

    suspend fun isDownloaded(trackId: String): Boolean {
        return downloadedSongDao.isDownloaded(trackId)
    }

    suspend fun getDownloadStatus(trackId: String): Flow<DownloadStatus> {
        return downloadedSongDao.getAllDownloadedSongs().map { list ->
            list.find { it.id == trackId }?.status ?: DownloadStatus.FAILED
        }
    }

    suspend fun startDownload(track: Track) {
        // Create initial entity with DOWNLOADING status
        val entity = DownloadedSongEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            thumbnailUrl = track.artworkUri.toString(),
            duration = track.duration,
            localPath = "", // Will be updated by worker
            status = DownloadStatus.DOWNLOADING
        )
        downloadedSongDao.insert(entity)

        // Start WorkManager job
        val data = workDataOf(
            "trackId" to track.id,
            "title" to track.title,
            "artist" to track.artist,
            "thumbnailUrl" to track.artworkUri.toString()
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "download_${track.id}",
                ExistingWorkPolicy.KEEP,
                downloadRequest
            )
    }

    suspend fun deleteDownload(trackId: String) {
        // Delete file (Task for worker or here?)
        // For simplicity, just delete DB entry. File cleanup should be done too.
        val entity = downloadedSongDao.getDownloadedSong(trackId)
        if (entity != null && entity.localPath.isNotEmpty()) {
            try {
                java.io.File(entity.localPath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        downloadedSongDao.delete(trackId)
        WorkManager.getInstance(context).cancelUniqueWork("download_$trackId")
    }
}
