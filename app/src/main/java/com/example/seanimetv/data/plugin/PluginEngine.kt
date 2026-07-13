package com.example.seanimetv.data.plugin

import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.data.plugin.model.SearchResult

/**
 * Narrow interface for the plugin engine's content-resolution capabilities.
 *
 * Extracted from [QuickJSEngine] to allow ViewModel unit tests to use a
 * fake implementation without the Android/native dependencies that [QuickJSEngine]
 * requires (Context, SharedPreferences, OkHttpClient).
 */
interface PluginEngine {
    suspend fun search(
        query: String,
        media: AniListMedia? = null,
        dub: Boolean = false
    ): Result<List<SearchResult>>
    suspend fun findEpisodes(searchId: String): Result<List<Episode>>
    suspend fun findEpisodeServer(episode: Episode, server: String): Result<EpisodeServer>
    fun getAvailableServers(): List<String>

    /** Loads (or reloads) the given plugin into the engine. Default: no-op for fakes. */
    suspend fun loadPlugin(entry: PluginEntry) {}
}
