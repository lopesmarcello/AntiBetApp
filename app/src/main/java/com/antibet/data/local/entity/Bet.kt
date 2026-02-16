package com.antibet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bets")
data class Bet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val amountCents: Long,
    val currency: String = "BRL",
    val note: String? = null,
    val category: String? = null,
    val source: String? = null,
)
