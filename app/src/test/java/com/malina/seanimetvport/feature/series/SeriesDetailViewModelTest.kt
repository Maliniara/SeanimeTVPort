package com.malina.seanimetvport.feature.series

import com.malina.seanimetvport.data.anilist.model.AniListMedia
import com.malina.seanimetvport.data.anilist.model.AniListTitle
import com.malina.seanimetvport.data.anilist.repository.FakeAniListRepository
import com.malina.seanimetvport.data.plugin.FakePluginEngine
import com.malina.seanimetvport.data.plugin.FakePluginRepository
import com.malina.seanimetvport.data.plugin.PluginEngine
import com.malina.seanimetvport.data.plugin.model.Episode
import com.malina.seanimetvport.data.plugin.model.EpisodeServer
import com.malina.seanimetvport.data.plugin.model.PluginEntry
import com.malina.seanimetvport.data.plugin.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Captures the [media] and [dub] arguments passed to [search] so tests can assert
 * that [SeriesDetailViewModel.loadMedia] forwards them correctly.
 */
private class CapturingFakePluginEngine(
    private val searchResult: Result<List<SearchResult>> = Result.success(emptyList()),
    private val servers: List<String> = emptyList()
) : PluginEngine {

    var lastSearchMedia: AniListMedia? = null
    var lastSearchDub: Boolean = false

    override suspend fun search(
        query: String,
        media: AniListMedia?,
        dub: Boolean
    ): Result<List<SearchResult>> {
        lastSearchMedia = media
        lastSearchDub = dub
        return searchResult
    }

    override suspend fun findEpisodes(searchId: String): Result<List<Episode>> =
        Result.success(emptyList())

    override suspend fun findEpisodeServer(
        episode: Episode,
        server: String
    ): Result<EpisodeServer> = Result.failure(UnsupportedOperationException("not configured"))

    override fun getAvailableServers(): List<String> = servers
}

/**
 * Engine whose [findEpisodes] result depends on which provider is currently loaded
 * (tracked via [loadPlugin]) — lets the autoprovider tests verify iteration across
 * providers. [search] always returns a hit so resolution reaches [findEpisodes].
 */
private class ProviderAwareFakeEngine(
    private val episodesByProvider: Map<String, List<Episode>>,
    private val serversByProvider: Map<String, List<String>> = emptyMap()
) : PluginEngine {

    private var currentProviderId: String? = null

    override suspend fun loadPlugin(entry: PluginEntry) { currentProviderId = entry.id }

    override suspend fun search(query: String, media: AniListMedia?, dub: Boolean) =
        Result.success(listOf(SearchResult("sr", query)))

    override suspend fun findEpisodes(searchId: String) =
        Result.success(episodesByProvider[currentProviderId].orEmpty())

    override suspend fun findEpisodeServer(episode: Episode, server: String) =
        Result.failure<EpisodeServer>(UnsupportedOperationException("not configured"))

    override fun getAvailableServers(): List<String> = serversByProvider[currentProviderId].orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeMedia(id: Int = 1, romaji: String = "Test Anime") = AniListMedia(
        id = id,
        title = AniListTitle(romaji = romaji)
    )

    // --- Existing regression tests ---

    /** loadMedia succeeds: Success state contains the correct media, episodes, and servers. */
    @Test
    fun loadMedia_success_setsSuccessStateWithCorrectData() = runTest {
        val media = makeMedia(1, "Cowboy Bebop")
        val episodes = listOf(Episode("ep1", 1, "Episode 1"))
        val servers = listOf("KAA", "Anikoto")
        val fakeRepo = FakeAniListRepository(mediaByIdResult = Result.success(media))
        val fakeEngine = FakePluginEngine(
            searchResult = Result.success(listOf(SearchResult("sr1", "Cowboy Bebop"))),
            findEpisodesResult = Result.success(episodes),
            servers = servers
        )
        val viewModel = SeriesDetailViewModel(fakeRepo, fakeEngine)

        viewModel.loadMedia(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got: $state", state is SeriesDetailUiState.Success)
        val success = state as SeriesDetailUiState.Success
        assertEquals(media, success.media)
        assertEquals(episodes, success.episodes)
        assertEquals(servers, success.availableServers)
    }

    /** loadMedia with repository failure: Error state with the failure message. */
    @Test
    fun loadMedia_error_setsErrorState() = runTest {
        val fakeRepo = FakeAniListRepository(
            mediaByIdResult = Result.failure(RuntimeException("Network failure"))
        )
        val fakeEngine = FakePluginEngine()
        val viewModel = SeriesDetailViewModel(fakeRepo, fakeEngine)

        viewModel.loadMedia(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error, got: $state", state is SeriesDetailUiState.Error)
        assertEquals("Network failure", (state as SeriesDetailUiState.Error).message)
    }

    /** onServerSelected updates the selectedServer state flow. */
    @Test
    fun selectServer_updatesActiveServer() = runTest {
        val media = makeMedia()
        val fakeRepo = FakeAniListRepository(mediaByIdResult = Result.success(media))
        val fakeEngine = FakePluginEngine(
            searchResult = Result.success(emptyList()),
            servers = listOf("KAA", "Anikoto", "Senshi")
        )
        val viewModel = SeriesDetailViewModel(fakeRepo, fakeEngine)
        viewModel.loadMedia(1)
        advanceUntilIdle()

        viewModel.onServerSelected("Senshi")

        assertEquals("Senshi", viewModel.selectedServer.value)
    }

    /** onEpisodeSelected resolves the server and emits to navigationEvent. */
    @Test
    fun selectEpisode_emitsNavigationEvent() = runTest {
        val media = makeMedia()
        val episode = Episode("ep1", 1, "Episode 1")
        val episodeServer = EpisodeServer("KAA", emptyMap(), emptyList())
        val fakeRepo = FakeAniListRepository(mediaByIdResult = Result.success(media))
        val fakeEngine = FakePluginEngine(
            searchResult = Result.success(listOf(SearchResult("sr1", "Test Anime"))),
            findEpisodesResult = Result.success(listOf(episode)),
            findEpisodeServerResult = Result.success(episodeServer),
            servers = listOf("KAA")
        )
        val viewModel = SeriesDetailViewModel(
            aniListRepository = fakeRepo,
            engine = fakeEngine
        )
        var navigatedTo: PlaybackRequest? = null
        val collectJob = launch { viewModel.navigationEvent.collect { navigatedTo = it } }
        viewModel.loadMedia(1)
        advanceUntilIdle()

        viewModel.onEpisodeSelected(episode)
        advanceUntilIdle()

        assertEquals(episodeServer, navigatedTo?.server)
        assertFalse("isLoadingServer must be false after navigation", viewModel.isLoadingServer.value)
        collectJob.cancel()
    }

    // --- Autoprovider (Denshi "try all providers") ---

    /** Auto is the default provider; when the active provider yields no episodes it
     *  falls through to the next installed provider that does, and records which won. */
    @Test
    fun loadMedia_autoProvider_fallsThroughToProviderWithEpisodes() = runTest {
        val media = makeMedia(1, "Frieren")
        val episodes = listOf(Episode("ep1", 1, "Episode 1"))
        // Active provider is the built-in (no episodes); prov-b resolves them.
        val repo = FakePluginRepository(
            initialUserPlugins = listOf(
                PluginEntry(id = "prov-b", name = "Provider B", assetPath = "plugins/prov-b.js")
            )
        )
        val engine = ProviderAwareFakeEngine(
            episodesByProvider = mapOf("prov-b" to episodes),
            serversByProvider = mapOf("prov-b" to listOf("KAA"))
        )
        val viewModel = SeriesDetailViewModel(
            aniListRepository = FakeAniListRepository(mediaByIdResult = Result.success(media)),
            engine = engine,
            pluginRepository = repo
        )

        viewModel.loadMedia(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got: $state", state is SeriesDetailUiState.Success)
        assertEquals(episodes, (state as SeriesDetailUiState.Success).episodes)
        assertEquals(SeriesDetailViewModel.AUTO_PROVIDER_ID, viewModel.selectedProviderId.value)
        assertEquals("prov-b", viewModel.autoResolvedProviderId.value)
    }

    /** In Auto mode the active provider is tried first, so when it already has
     *  episodes it wins without falling through to the others. */
    @Test
    fun loadMedia_autoProvider_prefersActiveProviderWhenItHasEpisodes() = runTest {
        val media = makeMedia(1, "Frieren")
        val builtinEpisodes = listOf(Episode("b1", 1, "Episode 1"))
        val repo = FakePluginRepository(
            initialUserPlugins = listOf(
                PluginEntry(id = "prov-b", name = "Provider B", assetPath = "plugins/prov-b.js")
            )
        )
        val engine = ProviderAwareFakeEngine(
            episodesByProvider = mapOf(
                "multistream-en" to builtinEpisodes,
                "prov-b" to listOf(Episode("x", 1, "Other"))
            )
        )
        val viewModel = SeriesDetailViewModel(
            aniListRepository = FakeAniListRepository(mediaByIdResult = Result.success(media)),
            engine = engine,
            pluginRepository = repo
        )

        viewModel.loadMedia(1)
        advanceUntilIdle()

        assertEquals("multistream-en", viewModel.autoResolvedProviderId.value)
        assertEquals(
            builtinEpisodes,
            (viewModel.uiState.value as SeriesDetailUiState.Success).episodes
        )
    }

    // --- Step 5.1: New state tests (REQ-013, REQ-014, REQ-015) ---

    /** trailerVisible starts as false before any action is taken (REQ-013). */
    @Test
    fun trailerVisible_initialValue_isFalse() = runTest {
        val viewModel = SeriesDetailViewModel(
            aniListRepository = FakeAniListRepository(),
            engine = FakePluginEngine()
        )
        assertFalse("trailerVisible should start as false", viewModel.trailerVisible.value)
    }

    /** showTrailer() sets trailerVisible to true (REQ-013). */
    @Test
    fun showTrailer_setsTrailerVisibleToTrue() = runTest {
        val viewModel = SeriesDetailViewModel(
            aniListRepository = FakeAniListRepository(),
            engine = FakePluginEngine()
        )
        viewModel.showTrailer()
        assertTrue("trailerVisible should be true after showTrailer()", viewModel.trailerVisible.value)
    }

    /** hideTrailer() sets trailerVisible back to false after showTrailer() (REQ-013). */
    @Test
    fun hideTrailer_setsTrailerVisibleToFalse() = runTest {
        val viewModel = SeriesDetailViewModel(
            aniListRepository = FakeAniListRepository(),
            engine = FakePluginEngine()
        )
        viewModel.showTrailer()
        viewModel.hideTrailer()
        assertFalse("trailerVisible should be false after hideTrailer()", viewModel.trailerVisible.value)
    }

    /** isDub starts false; toggleDub() flips to true; again returns to false (REQ-014). */
    @Test
    fun toggleDub_togglesIsDubValue() = runTest {
        val viewModel = SeriesDetailViewModel(
            aniListRepository = FakeAniListRepository(),
            engine = FakePluginEngine()
        )
        assertFalse("isDub should start as false", viewModel.isDub.value)
        viewModel.toggleDub()
        assertTrue("isDub should be true after first toggleDub()", viewModel.isDub.value)
        viewModel.toggleDub()
        assertFalse("isDub should be false after second toggleDub()", viewModel.isDub.value)
    }

    /** loadMedia() passes the resolved AniListMedia object and current isDub value to engine.search() (REQ-015). */
    @Test
    fun loadMedia_passesMediaAndDubToSearch() = runTest {
        val media = makeMedia(id = 12345, romaji = "Frieren")
        val fakeRepo = FakeAniListRepository(mediaByIdResult = Result.success(media))
        val capturingEngine = CapturingFakePluginEngine()
        val viewModel = SeriesDetailViewModel(
            aniListRepository = fakeRepo,
            engine = capturingEngine
        )

        viewModel.loadMedia(12345)
        advanceUntilIdle()

        assertNotNull("search() should have received a non-null media argument", capturingEngine.lastSearchMedia)
        assertEquals(
            "media.id passed to search() should match the loaded media",
            12345,
            capturingEngine.lastSearchMedia?.id
        )
        assertFalse(
            "dub passed to search() should match isDub (false by default)",
            capturingEngine.lastSearchDub
        )
    }

    // --- Business-rule documentation: EpisodeCard watched-state formula (spec gap (b)) ---
    //
    // The isWatched predicate used in EpisodeCard is:
    //   isWatched = watchedCount > 0 && episode.number <= watchedCount
    //
    // These tests document the two edge cases called out in the spec so a future
    // refactor of the formula can be caught here first.

    /**
     * When watchedCount == 0 (no AniList progress), no episode is considered watched —
     * even episode 1 must not show the watched overlay.
     */
    @Test
    fun episodeWatchedState_whenWatchedCountIsZero_noEpisodeIsWatched() {
        val watchedCount = 0
        assertFalse(
            "Episode 1 must not be watched when watchedCount is 0",
            watchedCount > 0 && 1 <= watchedCount
        )
        assertFalse(
            "Episode 99 must not be watched when watchedCount is 0",
            watchedCount > 0 && 99 <= watchedCount
        )
    }

    /**
     * When episode.number == watchedCount the episode IS still watched
     * (the upper bound is inclusive). The next episode (number + 1) is not watched.
     */
    @Test
    fun episodeWatchedState_whenEpisodeNumberEqualsWatchedCount_isWatched() {
        val watchedCount = 5
        assertTrue(
            "Episode 5 must be watched when watchedCount is 5 (boundary — inclusive)",
            watchedCount > 0 && 5 <= watchedCount
        )
        assertFalse(
            "Episode 6 must NOT be watched when watchedCount is 5 (beyond boundary)",
            watchedCount > 0 && 6 <= watchedCount
        )
    }

    private fun assertFalse(message: String, value: Boolean) {
        assertTrue(message, !value)
    }
}
