package com.audioflow.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val thumbnailUrl: String?,
    val duration: Long,
    val localPath: String,
    val status: DownloadStatus = DownloadStatus.COMPLETED,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED
}
