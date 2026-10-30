package com.malina.seanimetvport.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malina.seanimetvport.data.anilist.AccountRepository
import com.malina.seanimetvport.data.anilist.AniListAccount
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
import com.malina.seanimetvport.data.anilist.repository.AuthRepository
import com.malina.seanimetvport.data.settings.AnimeSettings
import com.malina.seanimetvport.data.settings.AnimeSettingsRepository
import com.malina.seanimetvport.data.tvchannel.TvChannelPublisher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SettingsUiState {
    object Loading : SettingsUiState()
    data class Loaded(
        val userName: String,
        val avatarUrl: String?
    ) : SettingsUiState()
    object LoggedOut : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
}

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val aniListRepository: AniListRepository,
    private val animeSettingsStore: AnimeSettingsRepository,
    private val accountRepository: AccountRepository,
    private val channelPublisher: TvChannelPublisher? = null,
    val onLoggedOut: () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** All signed-in accounts (incl. the local Guest), reactive. */
    val accounts: StateFlow<List<AniListAccount>> = accountRepository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeAccountId: StateFlow<Int?> = accountRepository.activeAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the Android TV home-screen Discovery channel is published. */
    private val _channelEnabled = MutableStateFlow(channelPublisher?.isEnabled() ?: false)
    val channelEnabled: StateFlow<Boolean> = _channelEnabled.asStateFlow()

    /** Denshi-style anime presentation preferences, reactive to store changes. */
    val animeSettings: StateFlow<AnimeSettings> = animeSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnimeSettings())

    fun setChannelEnabled(enabled: Boolean) {
        val publisher = channelPublisher ?: return
        viewModelScope.launch {
            if (enabled) publisher.enable() else publisher.disable()
            _channelEnabled.value = enabled
        }
    }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val activeId = accountRepository.activeAccountId.first()
            val active = accountRepository.listAccounts().find { it.id == activeId }
            if (active?.isGuest == true) {
                _uiState.value = SettingsUiState.Loaded(userName = active.name, avatarUrl = null)
                return@launch
            }
            aniListRepository.getViewer().fold(
                onSuccess = { user ->
                    _uiState.value = SettingsUiState.Loaded(user.name, user.avatar?.medium)
                },
                onFailure = {
                    _uiState.value = SettingsUiState.Loaded(active?.name ?: "Unknown", active?.avatarUrl)
                }
            )
        }
    }

    /** Logs out of the ACTIVE account. If it was the last, signals LoggedOut. */
    fun logout() {
        viewModelScope.launch {
            accountRepository.clearToken()
            if (accountRepository.listAccounts().isEmpty()) {
                _uiState.value = SettingsUiState.LoggedOut
                onLoggedOut()
            } else {
                loadSettings()
            }
        }
    }

    /** Adds the local Guest account and switches to it. */
    fun addGuest() {
        viewModelScope.launch {
            accountRepository.addOrUpdateAccount(AniListAccount.guestAccount())
            loadSettings()
        }
    }

    fun switchAccount(id: Int) {
        viewModelScope.launch {
            accountRepository.setActiveAccount(id)
            loadSettings()
        }
    }

    /** Removes an account. If none remain, signals LoggedOut. */
    fun removeAccount(id: Int) {
        viewModelScope.launch {
            accountRepository.removeAccount(id)
            if (accountRepository.listAccounts().isEmpty()) {
                _uiState.value = SettingsUiState.LoggedOut
                onLoggedOut()
            } else {
                loadSettings()
            }
        }
    }

    fun setHideSpoilers(value: Boolean) = viewModelScope.launch { animeSettingsStore.setHideSpoilers(value) }
    fun setHideAudienceScore(value: Boolean) = viewModelScope.launch { animeSettingsStore.setHideAudienceScore(value) }
    fun setEnableAdultContent(value: Boolean) = viewModelScope.launch { animeSettingsStore.setEnableAdultContent(value) }
    fun setBlurAdultContent(value: Boolean) = viewModelScope.launch { animeSettingsStore.setBlurAdultContent(value) }
    fun setDisableCardTrailers(value: Boolean) = viewModelScope.launch { animeSettingsStore.setDisableCardTrailers(value) }
}
