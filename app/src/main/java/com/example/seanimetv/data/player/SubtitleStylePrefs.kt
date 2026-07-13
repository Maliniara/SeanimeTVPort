package com.example.seanimetv.data.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User-customisable subtitle appearance & position. Persisted so the viewer sets
 * it once and every episode honours it. Position is a free-form dp offset applied
 * as a translation on the player's SubtitleView (0,0 = default bottom-centre).
 */
data class SubtitleStyle(
    val textSizeSp: Float = DEFAULT_SIZE_SP,
    val foregroundColor: Int = DEFAULT_COLOR,
    // Seanime Denshi look: white text with a black outline, NO background box.
    val backgroundEnabled: Boolean = DEFAULT_BG_ENABLED,
    val offsetXDp: Float = 0f,
    // Raised slightly off the bottom edge, matching Denshi's default position.
    val offsetYDp: Float = DEFAULT_OFFSET_Y_DP
) {
    companion object {
        const val DEFAULT_SIZE_SP = 24f
        const val MIN_SIZE_SP = 12f
        const val MAX_SIZE_SP = 44f
        const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
        const val DEFAULT_BG_ENABLED = false
        const val DEFAULT_OFFSET_Y_DP = -36f
        val DEFAULT = SubtitleStyle()
    }
}

private val Context.subtitleStyleDataStore by preferencesDataStore(name = "subtitle_style")

/** DataStore-backed persistence for [SubtitleStyle]. */
class SubtitleStylePrefs(private val context: Context) {

    private val sizeKey = floatPreferencesKey("size_sp")
    private val colorKey = intPreferencesKey("color")
    private val bgKey = booleanPreferencesKey("bg_enabled")
    private val offXKey = floatPreferencesKey("off_x_dp")
    private val offYKey = floatPreferencesKey("off_y_dp")

    val style: Flow<SubtitleStyle> = context.subtitleStyleDataStore.data.map { p ->
        SubtitleStyle(
            textSizeSp = p[sizeKey] ?: SubtitleStyle.DEFAULT_SIZE_SP,
            foregroundColor = p[colorKey] ?: SubtitleStyle.DEFAULT_COLOR,
            backgroundEnabled = p[bgKey] ?: SubtitleStyle.DEFAULT_BG_ENABLED,
            offsetXDp = p[offXKey] ?: 0f,
            offsetYDp = p[offYKey] ?: SubtitleStyle.DEFAULT_OFFSET_Y_DP
        )
    }

    suspend fun update(transform: (SubtitleStyle) -> SubtitleStyle) {
        context.subtitleStyleDataStore.edit { p ->
            val current = SubtitleStyle(
                textSizeSp = p[sizeKey] ?: SubtitleStyle.DEFAULT_SIZE_SP,
                foregroundColor = p[colorKey] ?: SubtitleStyle.DEFAULT_COLOR,
                backgroundEnabled = p[bgKey] ?: SubtitleStyle.DEFAULT_BG_ENABLED,
                offsetXDp = p[offXKey] ?: 0f,
                offsetYDp = p[offYKey] ?: SubtitleStyle.DEFAULT_OFFSET_Y_DP
            )
            val next = transform(current)
            p[sizeKey] = next.textSizeSp
            p[colorKey] = next.foregroundColor
            p[bgKey] = next.backgroundEnabled
            p[offXKey] = next.offsetXDp
            p[offYKey] = next.offsetYDp
        }
    }
}
