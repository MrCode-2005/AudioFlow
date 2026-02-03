package com.audioflow.player.model

import android.net.Uri

/**
 * Represents the source of a track
 */
enum class TrackSource {
    LOCAL,      // Local audio file from device storage
    YOUTUBE     // YouTube video (metadata only, no direct playback)
}

/**
 * Represents a music track in the app
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,             // Duration in milliseconds
    val artworkUri: Uri?,           // Album art URI (embedded or YouTube thumbnail)
    val contentUri: Uri,            // Content URI for playback
    val source: TrackSource = TrackSource.LOCAL,
    val dateAdded: Long = System.currentTimeMillis()
) {
    val formattedDuration: String
        get() {
            val minutes = (duration / 1000) / 60
            val seconds = (duration / 1000) % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

/**
 * Represents a playlist
 */
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val tracks: List<Track> = emptyList(),
    val artworkUri: Uri? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val trackCount: Int get() = tracks.size
    
    val totalDuration: Long get() = tracks.sumOf { it.duration }
    
    val formattedTotalDuration: String
        get() {
            val totalMinutes = (totalDuration / 1000) / 60
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes} min"
            }
        }
}

/**
 * Represents an album
 */
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUri: Uri?,
    val tracks: List<Track> = emptyList(),
    val year: Int? = null
) {
    val trackCount: Int get() = tracks.size
}

/**
 * Represents an artist
 */
data class Artist(
    val id: String,
    val name: String,
    val artworkUri: Uri? = null,
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList()
) {
    val albumCount: Int get() = albums.size
    val trackCount: Int get() = tracks.size
}

/**
 * YouTube video metadata (for link input feature)
 */
data class YouTubeMetadata(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val duration: Long? = null
)
