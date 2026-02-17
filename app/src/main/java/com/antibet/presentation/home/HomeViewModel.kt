package com.antibet.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antibet.data.repository.AntibetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn


data class HomeUiState(
    val totalSaved: Long = 0L,
    val totalBet: Long = 0L,
    val totalBalance: Long = 0L,
    val totalSavedThisMonth: Long = 0L,
    val totalBetThisMonth: Long = 0L,
    val streakDays: Int = 0,
    val isLoading: Boolean = true
)


class HomeViewModel(private val repository: AntibetRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getTotalSavedBetCents(),
        repository.getTotalBetCents(),
        repository.getTotalSavedThisMonth(),
        repository.getTotalBetThisMonth(),
    ) { saved, lost, monthSaved, monthLost ->
        HomeUiState(
            totalSaved = saved,
            totalBet = lost,
            totalBalance = saved - lost,
            totalSavedThisMonth = monthSaved,
            totalBetThisMonth = monthLost,
            streakDays = calculateStreak(),
            isLoading = false
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState()
    )

    private fun calculateStreak(): Int{
        //TODO
        return 0
    }
}