package com.malina.seanimetvport.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.animeSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anime_prefs"
)

/**
 * User preferences for anime presentation, mirroring Seanime Denshi's "Anime"
 * settings section. Persisted booleans, all defaulting to off except where noted.
 */
data class AnimeSettings(
    val hideSpoilers: Boolean = false,
    val hideAudienceScore: Boolean = false,
    val enableAdultContent: Boolean = false,
    val blurAdultContent: Boolean = false,
    val disableCardTrailers: Boolean = false
)

/** Reactive reader/writer for [AnimeSettings]. ViewModels depend on this interface
 *  so unit tests can inject an in-memory fake without an Android Context. */
interface AnimeSettingsRepository {
    val settings: Flow<AnimeSettings>
    suspend fun setHideSpoilers(value: Boolean)
    suspend fun setHideAudienceScore(value: Boolean)
    suspend fun setEnableAdultContent(value: Boolean)
    suspend fun setBlurAdultContent(value: Boolean)
    suspend fun setDisableCardTrailers(value: Boolean)
}

/** DataStore-backed [AnimeSettingsRepository]. */
class AnimeSettingsStore(private val context: Context) : AnimeSettingsRepository {

    private val HIDE_SPOILERS = booleanPreferencesKey("hide_spoilers")
    private val HIDE_AUDIENCE_SCORE = booleanPreferencesKey("hide_audience_score")
    private val ENABLE_ADULT_CONTENT = booleanPreferencesKey("enable_adult_content")
    private val BLUR_ADULT_CONTENT = booleanPreferencesKey("blur_adult_content")
    private val DISABLE_CARD_TRAILERS = booleanPreferencesKey("disable_card_trailers")

    override val settings: Flow<AnimeSettings> = context.animeSettingsDataStore.data.map { p ->
        AnimeSettings(
            hideSpoilers = p[HIDE_SPOILERS] ?: false,
            hideAudienceScore = p[HIDE_AUDIENCE_SCORE] ?: false,
            enableAdultContent = p[ENABLE_ADULT_CONTENT] ?: false,
            blurAdultContent = p[BLUR_ADULT_CONTENT] ?: false,
            disableCardTrailers = p[DISABLE_CARD_TRAILERS] ?: false
        )
    }

    override suspend fun setHideSpoilers(value: Boolean) = edit(HIDE_SPOILERS, value)
    override suspend fun setHideAudienceScore(value: Boolean) = edit(HIDE_AUDIENCE_SCORE, value)
    override suspend fun setEnableAdultContent(value: Boolean) = edit(ENABLE_ADULT_CONTENT, value)
    override suspend fun setBlurAdultContent(value: Boolean) = edit(BLUR_ADULT_CONTENT, value)
    override suspend fun setDisableCardTrailers(value: Boolean) = edit(DISABLE_CARD_TRAILERS, value)

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        context.animeSettingsDataStore.edit { it[key] = value }
    }
}
