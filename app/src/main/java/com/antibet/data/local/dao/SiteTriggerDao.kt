package com.antibet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.antibet.data.local.entity.SiteTrigger
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteTriggerDao {

    @Query("SELECT * FROM site_triggers ORDER BY timestamp DESC LIMIT 100")
    fun getRecentFlow(): Flow<List<SiteTrigger>>

    @Query("SELECT COUNT(*) FROM site_triggers WHERE timestamp >= :startTime")
    fun getCountSinceFlow(startTime: Long): Flow<Int>

    @Insert
    suspend fun insert(trigger: SiteTrigger): Long

    @Query("DELETE FROM site_triggers WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

}