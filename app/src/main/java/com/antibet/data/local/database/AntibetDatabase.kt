package com.antibet.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.antibet.data.local.dao.BetDao
import com.antibet.data.local.dao.SavedBetDao
import com.antibet.data.local.dao.SettingDao
import com.antibet.data.local.dao.SiteTriggerDao
import com.antibet.data.local.entity.Bet
import com.antibet.data.local.entity.SavedBet
import com.antibet.data.local.entity.Setting
import com.antibet.data.local.entity.SiteTrigger

@Database(
    entities = [SavedBet::class, Bet::class, SiteTrigger::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AntibetDatabase : RoomDatabase() {
    abstract fun betDao(): BetDao
    abstract fun savedBetDao(): SavedBetDao
    abstract fun siteTriggerDao(): SiteTriggerDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AntibetDatabase? = null

        fun getDatabase(context: Context): AntibetDatabase {
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AntibetDatabase::class.java,
                    "antibet_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
