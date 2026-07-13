package com.example.seanimetv.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One row of local playback history — reflects what was actually watched on this
 * device (the resume position included), independent of the AniList list. Used by
 * the Home "Recently watched" row and to resume playback from where it stopped.
 */
@Serializable
data class WatchHistoryEntry(
    val anilistId: Int,
    val title: String,
    val coverImageUrl: String,
    val bannerImageUrl: String? = null,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

private val Context.watchHistoryDataStore by preferencesDataStore(name = "watch_history")
private val HISTORY_KEY = stringPreferencesKey("history_json")
private const val MAX_KEPT = 30

/**
 * Local, DataStore-backed watch history. Upserts by [WatchHistoryEntry.anilistId]
 * (one row per series — the latest episode + position wins) and keeps the list
 * ordered most-recent-first. Persisted as a JSON array.
 */
class WatchHistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Most-recent-first history; [limit] rows (Home shows 10). */
    fun history(limit: Int = 10): Flow<List<WatchHistoryEntry>> =
        context.watchHistoryDataStore.data.map { prefs ->
            decode(prefs[HISTORY_KEY]).take(limit)
        }

    /** Saved resume position for a specific series+episode, or null. */
    suspend fun positionFor(anilistId: Int, episodeNumber: Int): Long? {
        val prefs = context.watchHistoryDataStore.data.first()
        return decode(prefs[HISTORY_KEY])
            .firstOrNull { it.anilistId == anilistId && it.episodeNumber == episodeNumber }
            ?.positionMs
    }

    /** Upserts one series' latest watch state, moving it to the front. */
    suspend fun record(entry: WatchHistoryEntry) {
        context.watchHistoryDataStore.edit { prefs ->
            val current = decode(prefs[HISTORY_KEY]).filterNot { it.anilistId == entry.anilistId }
            val updated = (listOf(entry) + current)
                .sortedByDescending { it.updatedAt }
                .take(MAX_KEPT)
            prefs[HISTORY_KEY] = json.encodeToString(updated)
        }
    }

    private fun decode(raw: String?): List<WatchHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<WatchHistoryEntry>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }
}
