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
import android.os.Environment
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedSongDao: DownloadedSongDao,
    private val downloadFolderDao: com.audioflow.player.data.local.dao.DownloadFolderDao,
    private val mediaRepository: MediaRepository
) {
    val allDownloads: Flow<List<DownloadedSongEntity>> = downloadedSongDao.getAllDownloadedSongs()
    val allFolders: Flow<List<com.audioflow.player.data.local.entity.DownloadFolderEntity>> = downloadFolderDao.getAllFolders()

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
    
    // ========== Folder Management ==========
    
    suspend fun createFolder(name: String): String {
        val folder = com.audioflow.player.data.local.entity.DownloadFolderEntity(name = name)
        downloadFolderDao.insert(folder)
        return folder.id
    }
    
    suspend fun renameFolder(folderId: String, newName: String) {
        downloadFolderDao.rename(folderId, newName)
    }
    
    suspend fun deleteFolder(folderId: String) {
        // Move songs back to root (unfolder them)
        val songs = downloadedSongDao.getAllDownloadedSongsSync()
        songs.filter { it.folderId == folderId }.forEach {
            downloadedSongDao.updateFolderId(it.id, null)
        }
        downloadFolderDao.delete(folderId)
    }
    
    suspend fun moveSongToFolder(songId: String, folderId: String?) {
        downloadedSongDao.updateFolderId(songId, folderId)
    }
    
    fun getSongsByFolder(folderId: String): Flow<List<DownloadedSongEntity>> {
        return downloadedSongDao.getSongsByFolder(folderId)
    }
    
    fun getUnfolderedSongs(): Flow<List<DownloadedSongEntity>> {
        return downloadedSongDao.getUnfolderedSongs()
    }
    
    suspend fun getSongCountInFolder(folderId: String): Int {
        return downloadedSongDao.getSongCountInFolder(folderId)
    }
    
    // ========== Save to Device (Public Storage) ==========
    
    /**
     * Save a track's audio file to the public "Songs 🎶" folder on internal storage.
     * Uses MediaStore API for Android 10+ (scoped storage compatible).
     * For YouTube tracks: extracts audio URL and downloads.
     * For local tracks: copies the file from content URI.
     */
    suspend fun saveToDevice(track: Track): Result<String> {
        return try {
            val safeTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val artist = (track.artist ?: "Unknown").replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            if (track.id.startsWith("yt_")) {
                // YouTube track — extract and download audio
                val videoId = track.id.removePrefix("yt_")
                val streamInfo = mediaRepository.getYouTubeStreamUrl(videoId).getOrElse { error ->
                    throw Exception("Could not get audio URL: ${error.message}")
                }
                
                val downloadUrl = streamInfo.audioStreamUrl
                if (downloadUrl.isBlank()) throw Exception("Empty audio URL")
                
                val fileName = "$safeTitle - $artist.m4a"
                
                // Download with OkHttp using same UA as extraction
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip")
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Download failed: HTTP ${response.code}")
                
                val inputStream = response.body?.byteStream() ?: throw Exception("Empty response body")
                
                val savedPath = writeToPublicStorage(fileName, "audio/mp4", inputStream)
                
                Log.d("DownloadRepo", "Saved to device: $savedPath")
                Result.success(savedPath)
                
            } else {
                // Local track — copy from content URI
                val contentUri = track.contentUri
                if (contentUri == android.net.Uri.EMPTY) {
                    throw Exception("No content URI available")
                }
                
                val fileName = "$safeTitle - $artist.mp3"
                
                val inputStream = context.contentResolver.openInputStream(contentUri)
                    ?: throw Exception("Could not open audio file")
                
                val savedPath = writeToPublicStorage(fileName, "audio/mpeg", inputStream)
                
                Log.d("DownloadRepo", "Saved to device: $savedPath")
                Result.success(savedPath)
            }
        } catch (e: Exception) {
            Log.e("DownloadRepo", "Save to device failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Write an input stream to the public "Songs 🎶" folder using MediaStore API.
     * Compatible with Android 10+ scoped storage.
     */
    private fun writeToPublicStorage(
        fileName: String,
        mimeType: String,
        inputStream: java.io.InputStream
    ): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ — use MediaStore API
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Songs \uD83C\uDFB6")
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
            }
            
            val collection = android.provider.MediaStore.Audio.Media.getContentUri(
                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
            
            val uri = context.contentResolver.insert(collection, contentValues)
                ?: throw Exception("Failed to create MediaStore entry")
            
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    inputStream.use { input ->
                        input.copyTo(outputStream, bufferSize = 8192)
                    }
                } ?: throw Exception("Failed to open output stream")
                
                // Mark as complete (not pending anymore)
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                
                return "Songs \uD83C\uDFB6/$fileName"
            } catch (e: Exception) {
                // Clean up on failure
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Android 9 and below — direct file I/O
            val songsDir = java.io.File(
                Environment.getExternalStorageDirectory(),
                "Songs \uD83C\uDFB6"
            )
            if (!songsDir.exists()) songsDir.mkdirs()
            
            val outFile = java.io.File(songsDir, fileName)
            inputStream.use { input ->
                java.io.FileOutputStream(outFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            
            // Notify media scanner
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(outFile.absolutePath), null, null
            )
            
            return outFile.absolutePath
        }
    }
}
