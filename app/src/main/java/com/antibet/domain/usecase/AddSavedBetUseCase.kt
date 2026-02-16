package com.antibet.domain.usecase

import com.antibet.data.local.entity.SavedBet
import com.antibet.data.repository.AntibetRepository

class AddSavedBetUseCase(private val repository: AntibetRepository){

    suspend operator fun invoke(
        amountCents: Long,
        note: String? = null,
        category: String? = null,
        source: String? = null
    ): Long {
        val savedBet = SavedBet(
            timestamp = System.currentTimeMillis(),
            amountCents = amountCents,
            note = note,
            category = category,
            source = source
        )
        return repository.insertSavedBet(savedBet)
    }
}