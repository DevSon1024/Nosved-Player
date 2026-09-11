package com.devson.nvplayer.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vaultMedia: VaultEntity): Long

    @Query("SELECT * FROM vault_media ORDER BY dateAdded DESC")
    fun getAllVaultMediaFlow(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_media ORDER BY dateAdded DESC")
    suspend fun getAllVaultMedia(): List<VaultEntity>

    @Query("SELECT * FROM vault_media WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VaultEntity?

    @Query("SELECT * FROM vault_media WHERE vaultPath = :vaultPath LIMIT 1")
    suspend fun getByVaultPath(vaultPath: String): VaultEntity?

    @Query("UPDATE vault_media SET lastPlaybackPosition = :pos WHERE id = :id")
    suspend fun updatePlaybackPosition(id: Long, pos: Long)

    @Delete
    suspend fun delete(vaultMedia: VaultEntity)

    @Query("DELETE FROM vault_media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vault_media")
    suspend fun deleteAll()
}
