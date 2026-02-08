package com.audioflow.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audioflow.player.data.local.entity.DownloadedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedSongDao {
    @Query("SELECT * FROM downloaded_songs ORDER BY timestamp DESC")
    fun getAllDownloadedSongs(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM downloaded_songs WHERE id = :id")
    suspend fun getDownloadedSong(id: String): DownloadedSongEntity?

    @Query("SELECT COUNT(*) > 0 FROM downloaded_songs WHERE id = :id AND status = 'COMPLETED'")
    suspend fun isDownloaded(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: DownloadedSongEntity)

    @Query("DELETE FROM downloaded_songs WHERE id = :id")
    suspend fun delete(id: String)
    
    @Query("UPDATE downloaded_songs SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String) // Using String for enum for simplicity via TypeConverter
}
