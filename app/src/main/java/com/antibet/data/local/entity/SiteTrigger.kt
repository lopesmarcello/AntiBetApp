package com.antibet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_triggers")
data class SiteTrigger(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val domain: String,
    val appPackage: String? = null,
    val action: String // "warned", "blocked", "ignored"
)