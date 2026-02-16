package com.antibet.domain.usecase

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.antibet.data.repository.AntibetRepository
import kotlinx.coroutines.flow.Flow

class GetTotalSavedUseCase(private val repository: AntibetRepository) {

    operator fun invoke(): Flow<Long> = repository.getTotalSavedBetCents()

    fun thisMonth(): Flow<Long> = repository.getTotalSavedThisMonth()

    fun thisWeek(): Flow<Long> = repository.getTotalSavedThisWeek()
}