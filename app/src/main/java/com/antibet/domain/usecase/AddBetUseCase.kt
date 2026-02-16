package com.antibet.domain.usecase

import com.antibet.data.local.entity.Bet
import com.antibet.data.repository.AntibetRepository

class AddBetUseCase(private val repository: AntibetRepository) {

    suspend operator fun invoke(
        amountCents: Long,
        note: String? = null,
        category: String? = null,
        source: String? = null
    ): Long {
        val bet = Bet(
            timestamp = System.currentTimeMillis(),
            amountCents = amountCents,
            note = note,
            category = category,
            source = source
        )

        return repository.insertBet(bet)
    }
}