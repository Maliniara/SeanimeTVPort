package com.example.seanimetv.data.plugin

import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.plugin.model.SearchResult

/**
 * Test double for [PluginEngine]. All results are configurable per-test.
 */
class FakePluginEngine(
    var searchResult: Result<List<SearchResult>> = Result.success(emptyList()),
    var findEpisodesResult: Result<List<Episode>> = Result.success(emptyList()),
    var findEpisodeServerResult: Result<EpisodeServer> =
        Result.failure(UnsupportedOperationException("not configured in fake")),
    var servers: List<String> = emptyList()
) : PluginEngine {

    override suspend fun search(
        query: String,
        media: AniListMedia?,
        dub: Boolean
    ): Result<List<SearchResult>> = searchResult

    override suspend fun findEpisodes(searchId: String): Result<List<Episode>> =
        findEpisodesResult

    override suspend fun findEpisodeServer(episode: Episode, server: String): Result<EpisodeServer> =
        findEpisodeServerResult

    override fun getAvailableServers(): List<String> = servers
}
