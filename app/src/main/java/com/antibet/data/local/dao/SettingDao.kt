package com.antibet.data.local.dao

import androidx.compose.ui.Modifier
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antibet.data.local.entity.Setting
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {

    @Query("SELECT * FROM settings WHERE `key` = :getKey")
    fun getFlow(getKey: String): Flow<Setting?>

    @Query("SELECT * FROM settings WHERE `key` = :getKey")
    suspend fun get(getKey: String): Setting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: Setting)

    @Query("DELETE FROM settings WHERE `key` = :deleteKey")
    suspend fun delete(deleteKey : String)
}