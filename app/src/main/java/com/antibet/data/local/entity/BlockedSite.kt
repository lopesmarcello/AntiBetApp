package com.antibet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_sites")
data class BlockedSite(
    @PrimaryKey
    val domain: String,
    val addedAt: Long = System.currentTimeMillis(),
    val addedFrom: String // "manual" | "notification" | "save_entry"
)
