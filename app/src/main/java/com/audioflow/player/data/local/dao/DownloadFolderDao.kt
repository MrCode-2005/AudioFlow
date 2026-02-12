package com.audioflow.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audioflow.player.data.local.entity.DownloadFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadFolderDao {
    @Query("SELECT * FROM download_folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<DownloadFolderEntity>>

    @Query("SELECT * FROM download_folders ORDER BY createdAt DESC")
    suspend fun getAllFoldersSync(): List<DownloadFolderEntity>

    @Query("SELECT * FROM download_folders WHERE id = :id")
    suspend fun getFolder(id: String): DownloadFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: DownloadFolderEntity)

    @Query("UPDATE download_folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM download_folders WHERE id = :id")
    suspend fun delete(id: String)
}
