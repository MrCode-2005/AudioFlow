package com.audioflow.player.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.audioflow.player.model.Album
import com.audioflow.player.model.Artist
import com.audioflow.player.model.Track
import com.audioflow.player.model.TrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver
    
    private val albumArtUri = Uri.parse("content://media/external/audio/albumart")
    
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
            MediaStore.Audio.Media.DATA  // Add file path for filtering
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
                
                val artworkUri = ContentUris.withAppendedId(albumArtUri, albumId)
                
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
                
                val artworkUri = ContentUris.withAppendedId(albumArtUri, id)
                
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
            rowsDeleted > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
