package com.audioflow.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val thumbnailUrl: String?,
    val duration: Long,
    val timestamp: Long = System.currentTimeMillis()
)
