package com.audioflow.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audioflow.player.data.local.entity.LikedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongDao {
    @Query("SELECT * FROM liked_songs ORDER BY timestamp DESC")
    fun getAllLikedSongs(): Flow<List<LikedSongEntity>>

    @Query("SELECT COUNT(*) > 0 FROM liked_songs WHERE id = :id")
    suspend fun isLiked(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE id = :id")
    suspend fun delete(id: String)
}
