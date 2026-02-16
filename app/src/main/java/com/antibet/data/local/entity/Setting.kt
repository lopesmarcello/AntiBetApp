package com.antibet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Setting (
    @PrimaryKey
    val key: String,
    val value: String,
)


object Settings {
    const val VPN_ENABLED = "vpn_enabled"
    const val NOTIFICATIONS_DAILY_ENABLED = "notifications_daily_enabled"
    const val BLOCK_BETTING_DOMAINS = "block_betting_domains"
    const val STREAK_START_DATE = "streak_start_date"
    const val LAST_BET_DATE = "last_bet_date"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val VPN_CONSENT_GIVEN = "vpn_consent_given"
}