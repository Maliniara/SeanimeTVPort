package com.example.seanimetv.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AnimeSettingsRepository] for ViewModel unit tests. */
class FakeAnimeSettingsRepository(
    initial: AnimeSettings = AnimeSettings()
) : AnimeSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AnimeSettings> = state

    override suspend fun setHideSpoilers(value: Boolean) {
        state.value = state.value.copy(hideSpoilers = value)
    }

    override suspend fun setHideAudienceScore(value: Boolean) {
        state.value = state.value.copy(hideAudienceScore = value)
    }

    override suspend fun setEnableAdultContent(value: Boolean) {
        state.value = state.value.copy(enableAdultContent = value)
    }

    override suspend fun setBlurAdultContent(value: Boolean) {
        state.value = state.value.copy(blurAdultContent = value)
    }

    override suspend fun setDisableCardTrailers(value: Boolean) {
        state.value = state.value.copy(disableCardTrailers = value)
    }
}
