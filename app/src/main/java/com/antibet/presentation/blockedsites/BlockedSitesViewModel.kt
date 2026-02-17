package com.antibet.presentation.blockedsites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antibet.data.local.entity.BlockedSite
import com.antibet.data.repository.AntibetRepository
import com.antibet.domain.model.BettingDomainList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedSiteItem(
    val domain: String,
    val displayName: String?,
    val isBuiltIn: Boolean,
    val addedFrom: String? = null
)

data class BlockedSitesUiState(
    val items: List<BlockedSiteItem> = emptyList(),
    val isLoading: Boolean = true,
    val inputError: String? = null
)

class BlockedSitesViewModel(private val repository: AntibetRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedSitesUiState())
    val uiState: StateFlow<BlockedSitesUiState> = _uiState.asStateFlow()

    // Built-in domains as a set for fast lookup
    private val builtInDomains: Map<String, String> = BettingDomainList.domains
        .associate { it.domain to it.displayName }

    init {
        repository.getBlockedSites()
            .onEach { userSites -> buildList(userSites) }
            .launchIn(viewModelScope)
    }

    private fun buildList(userSites: List<BlockedSite>) {
        val userDomainSet = userSites.map { it.domain }.toSet()

        // User-added sites first (most recently added at the top)
        val userItems = userSites.map { site ->
            BlockedSiteItem(
                domain = site.domain,
                displayName = builtInDomains[site.domain],
                isBuiltIn = false,
                addedFrom = site.addedFrom
            )
        }

        // Built-in sites that the user has NOT manually added (shown read-only below)
        val builtInItems = builtInDomains
            .filter { (domain, _) -> domain !in userDomainSet }
            .map { (domain, name) ->
                BlockedSiteItem(
                    domain = domain,
                    displayName = name,
                    isBuiltIn = true
                )
            }
            .sortedBy { it.displayName ?: it.domain }

        _uiState.update {
            it.copy(items = userItems + builtInItems, isLoading = false, inputError = null)
        }
    }

    fun addSite(rawInput: String) {
        val normalized = rawInput
            .trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removeSuffix("/")

        if (!isValidDomain(normalized)) {
            _uiState.update { it.copy(inputError = "Domínio inválido. Ex: exemplo.com") }
            return
        }

        _uiState.update { it.copy(inputError = null) }

        viewModelScope.launch {
            repository.addBlockedSite(normalized, "manual")
        }
    }

    fun removeSite(domain: String) {
        viewModelScope.launch {
            repository.removeBlockedSite(domain)
        }
    }

    fun clearInputError() {
        _uiState.update { it.copy(inputError = null) }
    }

    private fun isValidDomain(domain: String): Boolean {
        return domain.isNotBlank() &&
                domain.length >= 4 &&
                domain.contains('.') &&
                Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$").matches(domain)
    }
}
