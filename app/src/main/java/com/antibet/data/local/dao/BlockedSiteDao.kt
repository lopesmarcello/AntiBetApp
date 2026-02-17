package com.antibet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antibet.data.local.entity.BlockedSite
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedSiteDao {

    @Query("SELECT * FROM blocked_sites ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<BlockedSite>>

    @Query("SELECT * FROM blocked_sites ORDER BY addedAt DESC")
    suspend fun getAll(): List<BlockedSite>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(site: BlockedSite): Long

    @Query("DELETE FROM blocked_sites WHERE domain = :domain")
    suspend fun delete(domain: String)

    @Query("SELECT COUNT(*) FROM blocked_sites WHERE domain = :domain")
    suspend fun exists(domain: String): Int
}
