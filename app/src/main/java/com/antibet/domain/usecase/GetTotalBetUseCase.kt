package com.antibet.domain.usecase

import com.antibet.data.repository.AntibetRepository
import kotlinx.coroutines.flow.Flow

class GetTotalBetUseCase(private val repository: AntibetRepository) {

    operator fun invoke(): Flow<Long> = repository.getTotalBetCents()

    fun thisMonth(): Flow<Long> = repository.getTotalBetThisMonth()

    fun thisWeek(): Flow<Long> = repository.getTotalBetThisWeek()

}