package com.antibet.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.antibet.data.local.entity.SavedBet
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedBetDao {

    @Query("SELECT * FROM saved_bets ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<SavedBet>>

    @Query("SELECT * FROM saved_bets WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getBetweenDatesFlow(startTime: Long, endTime: Long): Flow<List<SavedBet>>

    @Query("SELECT SUM(amountCents) FROM saved_bets")
    fun getTotalSavedCentsFlow(): Flow<Long?>

    @Query("SELECT SUM(amountCents) FROM saved_bets WHERE timestamp >= :startTime")
    fun getTotalSavedCentsSinceFlow(startTime: Long): Flow<Long?>

    @Query("SELECT * FROM saved_bets WHERE id = :id")
    suspend fun getById(id: Long): SavedBet?

    @Insert
    suspend fun insert(savedBet: SavedBet): Long

    @Update
    suspend fun update(savedBet: SavedBet)

    @Delete
    suspend  fun delete(savedBet: SavedBet)

    @Query("DELETE FROM saved_bets")
    suspend fun deleteAll();
}