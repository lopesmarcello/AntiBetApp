package com.antibet.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.antibet.data.local.entity.Bet
import kotlinx.coroutines.flow.Flow
import java.sql.Time

@Dao
interface BetDao {

    @Query("SELECT * FROM bets ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<Bet>>

    @Query("SELECT * FROM bets WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getBetweenDatesFlow(startTime: Long, endTime: Time): Flow<List<Bet>>

    @Query("SELECT SUM(amountCents) FROM bets")
    fun getTotalWastedCentsFlow(): Flow<Long?>

    @Query("SELECT SUM(amountCents) FROM bets WHERE timestamp >= :startTime")
    fun getTotalWastedCentsSinceFlow(startTime: Long): Flow<Long?>

    @Insert
    suspend fun insert(bet: Bet): Long

    @Update
    suspend fun update(bet: Bet)

    @Delete
    suspend fun delete(bet: Bet)

    @Query("DELETE FROM bets")
    suspend fun deleteAll()
}