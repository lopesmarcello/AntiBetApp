package com.antibet.data.repository

import com.antibet.data.local.dao.BetDao
import com.antibet.data.local.dao.SavedBetDao
import com.antibet.data.local.dao.SettingDao
import com.antibet.data.local.dao.SiteTriggerDao
import com.antibet.data.local.entity.Bet
import com.antibet.data.local.entity.SavedBet
import com.antibet.data.local.entity.Setting
import com.antibet.data.local.entity.SiteTrigger
import com.antibet.util.CalendarUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AntibetRepository(
    private val betDao: BetDao,
    private val savedBetDao: SavedBetDao,
    private val siteTriggerDao: SiteTriggerDao,
    private val settingDao: SettingDao
) {
    // bets

    fun getAllBets(): Flow<List<Bet>> = betDao.getAllFlow()
    fun getAllSavedBets(): Flow<List<SavedBet>> = savedBetDao.getAllFlow()

    fun getTotalBetCents(): Flow<Long> = betDao.getTotalWastedCentsFlow().map { it ?: 0L }
    fun getTotalSavedBetCents(): Flow<Long> = savedBetDao.getTotalSavedCentsFlow().map { it ?: 0L }

    fun getTotalBetThisMonth(): Flow<Long>{
        val startTime = CalendarUtils.getStartOfMonthInMillis()
        return betDao.getTotalWastedCentsSinceFlow(startTime).map { it ?: 0L}
    }

    fun getTotalSavedThisMonth(): Flow<Long> {
        val startTime = CalendarUtils.getStartOfMonthInMillis()
        return savedBetDao.getTotalSavedCentsSinceFlow(startTime).map { it ?: 0L }
    }

    fun getTotalBetThisWeek(): Flow<Long> {
        val startTime = CalendarUtils.getStartOfWeekInMillis()
        return betDao.getTotalWastedCentsSinceFlow(startTime).map { it ?: 0L}
    }

    fun getTotalSavedThisWeek(): Flow<Long> {
        val startTime = CalendarUtils.getStartOfWeekInMillis()
        return savedBetDao.getTotalSavedCentsSinceFlow(startTime).map { it ?: 0L}
    }

    suspend fun insertSavedBet(savedBet: SavedBet): Long = savedBetDao.insert(savedBet)
    suspend fun updateSavedBet(savedBet: SavedBet) = savedBetDao.update(savedBet)
    suspend fun deleteSavedBet(savedBet: SavedBet) = savedBetDao.delete(savedBet)

    suspend fun insertBet(bet: Bet): Long = betDao.insert(bet)
    suspend fun updateBet(bet: Bet) = betDao.update(bet)
    suspend fun deleteBet(bet: Bet) = betDao.delete(bet)


    // site trigger
    fun getRecentTriggers(): Flow<List<SiteTrigger>> = siteTriggerDao.getRecentFlow();

    fun getTriggersCountToday(): Flow<Int>{
        val startDay = CalendarUtils.getTodayInMillis()
        return siteTriggerDao.getCountSinceFlow(startDay)
    }

    suspend fun insertTrigger(trigger: SiteTrigger): Long = siteTriggerDao.insert(trigger)

    // settings

    fun getSettingFlow(key: String): Flow<String?> =
        settingDao.getFlow(key).map { it?.value }

    suspend fun getSetting(key: String): String? =
        settingDao.get(key)?.value

    suspend fun setSetting(key: String, value: String) =
        settingDao.insert(Setting(key, value))

    suspend fun deleteSetting(key: String) =
        settingDao.delete(key)


}