package com.antibet.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antibet.data.local.entity.Settings
import com.antibet.data.repository.AntibetRepository
import com.antibet.util.CalendarUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


data class HomeUiState(
    val totalSaved: Long = 0L,
    val totalBet: Long = 0L,
    val totalBalance: Long = 0L,
    val totalSavedThisMonth: Long = 0L,
    val totalBetThisMonth: Long = 0L,
    val streakDays: Int = 0,
    val isLoading: Boolean = true
)

private const val ONE_DAY_MILLIS = 86_400_000L

class HomeViewModel(private val repository: AntibetRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            val existing = repository.getSetting(Settings.STREAK_START_DATE)
            if (existing == null) {
                repository.setSetting(
                    Settings.STREAK_START_DATE,
                    CalendarUtils.getTodayInMillis().toString()
                )
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getTotalSavedBetCents(),
        repository.getTotalBetCents(),
        repository.getTotalSavedThisMonth(),
        repository.getTotalBetThisMonth(),
        repository.getSettingFlow(Settings.STREAK_START_DATE),
    ) { saved, lost, monthSaved, monthLost, streakStartDate ->
        HomeUiState(
            totalSaved = saved,
            totalBet = lost,
            totalBalance = saved - lost,
            totalSavedThisMonth = monthSaved,
            totalBetThisMonth = monthLost,
            streakDays = calculateStreak(streakStartDate),
            isLoading = false
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState()
    )

    private fun calculateStreak(streakStartDate: String?): Int {
        if (streakStartDate == null) return 0
        val startMillis = streakStartDate.toLongOrNull() ?: return 0
        val todayMillis = CalendarUtils.getTodayInMillis()
        val days = ((todayMillis - startMillis) / ONE_DAY_MILLIS).toInt()
        return days.coerceAtLeast(0)
    }
}
