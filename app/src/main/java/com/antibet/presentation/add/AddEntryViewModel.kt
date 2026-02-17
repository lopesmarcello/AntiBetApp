package com.antibet.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antibet.data.local.entity.Bet
import com.antibet.data.local.entity.SavedBet
import com.antibet.data.local.entity.Settings
import com.antibet.data.repository.AntibetRepository
import com.antibet.util.CalendarUtils
import kotlinx.coroutines.launch

class AddEntryViewModel(private val repository: AntibetRepository) : ViewModel() {

    fun saveEntry(amountCents: Long, note: String, isSaved: Boolean, domain: String? = null) {
        viewModelScope.launch {
            if (isSaved) {
                repository.insertSavedBet(
                    SavedBet(
                        timestamp = System.currentTimeMillis(),
                        amountCents = amountCents,
                        note = note,
                        source = domain
                    )
                )
            } else {
                repository.insertBet(
                    Bet(
                        timestamp = System.currentTimeMillis(),
                        amountCents = amountCents,
                        note = note
                    )
                )
                val todayMillis = CalendarUtils.getTodayInMillis().toString()
                repository.setSetting(Settings.STREAK_START_DATE, todayMillis)
                repository.setSetting(Settings.LAST_BET_DATE, todayMillis)
            }
        }
    }
}
