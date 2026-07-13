package com.example.seanimetv.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")
private val QUERIES_KEY = stringPreferencesKey("queries_json")
private const val MAX_KEPT = 10

/**
 * Recent search phrases for the Search screen, most-recent-first. Upserts
 * case-insensitively (re-searching a phrase moves it to the front) and keeps
 * at most [MAX_KEPT] entries. Persisted as a JSON string array.
 */
class SearchHistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val history: Flow<List<String>> =
        context.searchHistoryDataStore.data.map { prefs -> decode(prefs[QUERIES_KEY]) }

    suspend fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = decode(prefs[QUERIES_KEY])
                .filterNot { it.equals(trimmed, ignoreCase = true) }
            prefs[QUERIES_KEY] = json.encodeToString(listOf(trimmed) + current.take(MAX_KEPT - 1))
        }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }
}
