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
    
    /**
     * Get current download entity (null if not downloaded/downloading)
     */
    suspend fun getDownloadEntity(trackId: String): DownloadedSongEntity? {
        return downloadedSongDao.getDownloadedSong(trackId)
    }
    
    /**
     * Toggle download state: 
     * - If not downloaded, start download
     * - If downloading, cancel download
     * - If completed, do nothing (already downloaded)
     * Returns the new status
     */
    suspend fun toggleDownload(track: Track): DownloadStatus {
        val existing = downloadedSongDao.getDownloadedSong(track.id)
        
        return when (existing?.status) {
            DownloadStatus.COMPLETED -> DownloadStatus.COMPLETED // Already done
            DownloadStatus.DOWNLOADING -> {
                // Cancel the download
                cancelDownload(track.id)
                DownloadStatus.FAILED
            }
            else -> {
                // Start new download
                startDownload(track)
                DownloadStatus.DOWNLOADING
            }
        }
    }
    
    /**
     * Cancel an in-progress download
     */
    suspend fun cancelDownload(trackId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$trackId")
        downloadedSongDao.delete(trackId)
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
        // Delete file and DB entry
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
    
    /**
     * Delete all downloads
     */
    suspend fun deleteAllDownloads() {
        val all = downloadedSongDao.getAllDownloadedSongsSync()
        all.forEach { entity ->
            if (entity.localPath.isNotEmpty()) {
                try {
                    java.io.File(entity.localPath).delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            WorkManager.getInstance(context).cancelUniqueWork("download_${entity.id}")
        }
        downloadedSongDao.deleteAll()
    }
    
    /**
     * Reorder song in downloads list
     */
    suspend fun reorderDownload(trackId: String, moveUp: Boolean) {
        val songs = downloadedSongDao.getAllDownloadedSongsSync().sortedByDescending { it.timestamp }
        val index = songs.indexOfFirst { it.id == trackId }
        if (index < 0) return
        
        val newIndex = if (moveUp) index - 1 else index + 1
        if (newIndex < 0 || newIndex >= songs.size) return
        
        // Swap timestamp to change order
        val current = songs[index]
        val target = songs[newIndex]
        downloadedSongDao.insert(current.copy(timestamp = target.timestamp))
        downloadedSongDao.insert(target.copy(timestamp = current.timestamp))
    }
    
    /**
     * Retry a failed download
     */
    suspend fun retryDownload(trackId: String) {
        val entity = downloadedSongDao.getDownloadedSong(trackId) ?: return
        if (entity.status != DownloadStatus.FAILED) return
        
        // Reset status to downloading
        downloadedSongDao.insert(entity.copy(status = DownloadStatus.DOWNLOADING))
        
        // Restart WorkManager job
        val data = workDataOf(
            "trackId" to entity.id,
            "title" to entity.title,
            "artist" to entity.artist,
            "thumbnailUrl" to entity.thumbnailUrl
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${entity.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
