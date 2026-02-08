package com.audioflow.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.audioflow.player.data.local.dao.DownloadedSongDao
import com.audioflow.player.data.local.dao.LikedSongDao
import com.audioflow.player.data.local.entity.DownloadStatus
import com.audioflow.player.data.local.entity.DownloadedSongEntity
import com.audioflow.player.data.local.entity.LikedSongEntity

@Database(
    entities = [LikedSongEntity::class, DownloadedSongEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedSongDao(): LikedSongDao
    abstract fun downloadedSongDao(): DownloadedSongDao
}

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDownloadStatus(status: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(status)
        } catch (e: Exception) {
            DownloadStatus.FAILED
        }
    }
}
