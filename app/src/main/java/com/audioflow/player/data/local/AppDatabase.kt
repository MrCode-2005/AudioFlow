package com.audioflow.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.audioflow.player.data.local.dao.DownloadedSongDao
import com.audioflow.player.data.local.dao.DownloadFolderDao
import com.audioflow.player.data.local.dao.LikedSongDao
import com.audioflow.player.data.local.entity.DownloadStatus
import com.audioflow.player.data.local.entity.DownloadedSongEntity
import com.audioflow.player.data.local.entity.DownloadFolderEntity
import com.audioflow.player.data.local.entity.LikedSongEntity

@Database(
    entities = [LikedSongEntity::class, DownloadedSongEntity::class, DownloadFolderEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedSongDao(): LikedSongDao
    abstract fun downloadedSongDao(): DownloadedSongDao
    abstract fun downloadFolderDao(): DownloadFolderDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add folderId column to downloaded_songs
                db.execSQL("ALTER TABLE downloaded_songs ADD COLUMN folderId TEXT DEFAULT NULL")
                // Create download_folders table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS download_folders (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }
    }
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
