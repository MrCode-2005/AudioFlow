package com.audioflow.player.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.audioflow.player.model.Album
import com.audioflow.player.model.Artist
import com.audioflow.player.model.Track
import com.audioflow.player.model.TrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver
    
    // Cache directory for album art
    private val artworkCacheDir: File by lazy {
        File(context.cacheDir, "album_art").also { it.mkdirs() }
    }
    
    suspend fun scanAllTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA  // File path for artwork extraction
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        // Paths to exclude (messaging apps, ringtones, notifications, etc.)
        val excludedPaths = listOf(
            "whatsapp",
            "telegram",
            "messenger",
            "signal",
            "viber",
            "ringtones",
            "notifications",
            "alarms",
            "recordings"
        )
        
        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            
            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataColumn) ?: ""
                
                // Skip files from excluded paths
                val isExcluded = excludedPaths.any { excluded ->
                    filePath.lowercase().contains(excluded)
                }
                if (isExcluded) continue
                
                // Skip very short audio files (likely sound effects or voice notes)
                val duration = cursor.getLong(durationColumn)
                if (duration < 30000) continue  // Skip files shorter than 30 seconds
                
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                // Extract and cache embedded album art
                val artworkUri = getOrExtractArtwork(id.toString(), filePath, albumId)
                
                tracks.add(
                    Track(
                        id = id.toString(),
                        title = cursor.getString(titleColumn) ?: "Unknown Title",
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                        album = cursor.getString(albumColumn) ?: "Unknown Album",
                        duration = duration,
                        artworkUri = artworkUri,
                        contentUri = contentUri,
                        source = TrackSource.LOCAL,
                        dateAdded = cursor.getLong(dateAddedColumn) * 1000
                    )
                )
            }
        }
        
        tracks
    }
    
    /**
     * Extract embedded album art from audio file using MediaMetadataRetriever.
     * Caches the result to avoid re-extraction on subsequent scans.
     */
    private fun getOrExtractArtwork(trackId: String, filePath: String, albumId: Long): Uri? {
        val cacheFile = File(artworkCacheDir, "track_$trackId.jpg")
        
        // Return cached artwork if exists
        if (cacheFile.exists()) {
            return cacheFile.toUri()
        }
        
        // Try to extract embedded artwork using MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val embeddedArt = retriever.embeddedPicture
            
            if (embeddedArt != null) {
                // Decode and save the artwork
                val bitmap = BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size)
                if (bitmap != null) {
                    // Scale down if too large (max 512x512) to save memory
                    val scaledBitmap = scaleBitmap(bitmap, 512)
                    FileOutputStream(cacheFile).use { fos ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                    }
                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }
                    bitmap.recycle()
                    return cacheFile.toUri()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release exceptions
            }
        }
        
        // Fallback: Try the album art content URI (may work on some devices)
        val albumArtUri = Uri.parse("content://media/external/audio/albumart")
        val fallbackUri = ContentUris.withAppendedId(albumArtUri, albumId)
        
        // Check if album art exists at the URI
        try {
            contentResolver.openInputStream(fallbackUri)?.use {
                return fallbackUri
            }
        } catch (e: Exception) {
            // Album art not available
        }
        
        return null
    }
    
    /**
     * Scale bitmap to max dimension while maintaining aspect ratio
     */
    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val ratio = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albumsMap = mutableMapOf<String, Album>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )
        
        contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Albums.ALBUM} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumName = cursor.getString(albumColumn) ?: "Unknown Album"
                
                // Get artwork from cached track artwork or album art URI
                val artworkUri = getAlbumArtwork(id)
                
                albumsMap[id.toString()] = Album(
                    id = id.toString(),
                    name = albumName,
                    artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                    artworkUri = artworkUri
                )
            }
        }
        
        albumsMap.values.toList()
    }
    
    /**
     * Get artwork for an album - tries cached track art first, then album art URI
     */
    private fun getAlbumArtwork(albumId: Long): Uri? {
        // Check if we have any cached artwork for tracks in this album
        val albumCacheFile = File(artworkCacheDir, "album_$albumId.jpg")
        if (albumCacheFile.exists()) {
            return albumCacheFile.toUri()
        }
        
        // Fallback to album art content URI
        val albumArtUri = Uri.parse("content://media/external/audio/albumart")
        val fallbackUri = ContentUris.withAppendedId(albumArtUri, albumId)
        
        try {
            contentResolver.openInputStream(fallbackUri)?.use {
                return fallbackUri
            }
        } catch (e: Exception) {
            // Album art not available
        }
        
        return null
    }
    
    suspend fun getArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val artistsMap = mutableMapOf<String, Artist>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Artists.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )
        
        contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Artists.ARTIST} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val artistName = cursor.getString(artistColumn) ?: "Unknown Artist"
                
                artistsMap[id.toString()] = Artist(
                    id = id.toString(),
                    name = artistName
                )
            }
        }
        
        artistsMap.values.toList()
    }
    
    suspend fun getTracksForAlbum(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        scanAllTracks().filter { it.album == albumId || getAlbumIdForTrack(it.id) == albumId }
    }
    
    private fun getAlbumIdForTrack(trackId: String): String? {
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(trackId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0).toString()
            }
        }
        return null
    }
    
    /**
     * Delete a track from the device storage
     * Returns true if deletion was successful
     */
    suspend fun deleteTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                trackId.toLong()
            )
            val rowsDeleted = contentResolver.delete(uri, null, null)
            
            // Also delete cached artwork
            if (rowsDeleted > 0) {
                File(artworkCacheDir, "track_$trackId.jpg").delete()
            }
            
            rowsDeleted > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Clear the artwork cache (useful for forcing a refresh)
     */
    fun clearArtworkCache() {
        artworkCacheDir.listFiles()?.forEach { it.delete() }
    }
}
