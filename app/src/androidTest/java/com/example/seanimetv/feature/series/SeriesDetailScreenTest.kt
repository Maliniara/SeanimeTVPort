package com.example.seanimetv.feature.series

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListTitle
import com.example.seanimetv.data.anilist.model.AniListTrailer
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.data.plugin.PluginEngine
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.plugin.model.SearchResult
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// ---------------------------------------------------------------------------
// Inline fake helpers — not shared with unit tests (different source set)
// ---------------------------------------------------------------------------

private fun fakeRepo(media: AniListMedia) = object : AniListRepository {
    override suspend fun getViewer(): Result<AniListUser> =
        Result.failure(UnsupportedOperationException())
    override suspend fun getMediaList(userId: Int, status: MediaListStatus): Result<List<AniListMediaListEntry>> =
        Result.failure(UnsupportedOperationException())
    override suspend fun getMediaById(id: Int): Result<AniListMedia> = Result.success(media)
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus): Result<Unit> =
        Result.success(Unit)
}

/** Engine returning no episodes and the given server list. */
private fun fakeEngine(
    servers: List<String> = emptyList(),
    episodes: List<Episode> = emptyList()
) = object : PluginEngine {
    override suspend fun search(
        query: String, media: AniListMedia?, dub: Boolean
    ): Result<List<SearchResult>> =
        if (episodes.isEmpty()) Result.success(emptyList())
        else Result.success(listOf(SearchResult(id = "sr1", title = query)))

    override suspend fun findEpisodes(searchId: String): Result<List<Episode>> =
        Result.success(episodes)

    override suspend fun findEpisodeServer(episode: Episode, server: String): Result<EpisodeServer> =
        Result.failure(UnsupportedOperationException())

    override fun getAvailableServers(): List<String> = servers
}

private fun baseMedia(
    trailer: AniListTrailer? = null,
    title: String = "Test Anime"
) = AniListMedia(
    id = 1,
    title = AniListTitle(romaji = title, english = title),
    trailer = trailer
)

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

@RunWith(AndroidJUnit4::class)
class SeriesDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * (a) Trailer button NOT in composition when media.trailer == null.
     * Verifies REQ-004: conditional Trailer button.
     */
    @Test
    fun trailerButton_notShown_whenTrailerIsNull() {
        val media = baseMedia(trailer = null)
        val vm = SeriesDetailViewModel(fakeRepo(media), fakeEngine())
        composeTestRule.setContent {
            SeanimeTVTheme {
                SeriesDetailScreen(viewModel = vm, anilistId = 1)
            }
        }
        // Title appears in the screen (may be in multiple places — sidebar + header)
        composeTestRule.onAllNodesWithText("Test Anime")[0].assertIsDisplayed()
        // Trailer button must be absent when trailer is null
        composeTestRule.onNodeWithText("Trailer").assertDoesNotExist()
    }

    /**
     * (b) Trailer button IS in composition when media.trailer.site == "youtube" and id != null.
     * Verifies REQ-004.
     */
    @Test
    fun trailerButton_shown_whenTrailerIsYouTube() {
        val media = baseMedia(trailer = AniListTrailer(site = "youtube", id = "abc123"))
        val vm = SeriesDetailViewModel(fakeRepo(media), fakeEngine())
        composeTestRule.setContent {
            SeanimeTVTheme {
                SeriesDetailScreen(viewModel = vm, anilistId = 1)
            }
        }
        composeTestRule.onNodeWithText("Trailer").assertIsDisplayed()
    }

    /**
     * (c) Sub/Dub toggle starts with label "Sub" (isDub=false) and switches to "Dub"
     * after toggleDub() is called.
     * Verifies REQ-005: Sub/Dub toggle as ActionButton in Source LazyRow.
     * Requires at least one server so the Source LazyRow renders.
     */
    @Test
    fun subDubToggle_initiallyShowsSub_thenDub_afterToggle() {
        val media = baseMedia()
        val vm = SeriesDetailViewModel(fakeRepo(media), fakeEngine(servers = listOf("TestServer")))
        composeTestRule.setContent {
            SeanimeTVTheme {
                SeriesDetailScreen(viewModel = vm, anilistId = 1)
            }
        }
        // Initially isDub == false → label "Sub"
        composeTestRule.onNodeWithText("Sub").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dub").assertDoesNotExist()

        // Toggle dub
        vm.toggleDub()
        composeTestRule.waitForIdle()

        // After toggle isDub == true → label "Dub"
        composeTestRule.onNodeWithText("Dub").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sub").assertDoesNotExist()
    }

    /**
     * (d) EpisodeCard renders an AsyncImage semantic node when episode.thumbnail != null.
     * Verified via the contentDescription set on AsyncImage ("Episode N: Title").
     * Verifies REQ-001 (thumbnail in wide card format).
     */
    @Test
    fun episodeCard_showsImageNode_whenThumbnailPresent() {
        val episodes = listOf(
            Episode(id = "ep1", number = 1, title = "The Journey's End", thumbnail = "https://example.com/thumb.jpg")
        )
        val media = baseMedia()
        val vm = SeriesDetailViewModel(fakeRepo(media), fakeEngine(episodes = episodes))
        composeTestRule.setContent {
            SeanimeTVTheme {
                SeriesDetailScreen(viewModel = vm, anilistId = 1)
            }
        }
        // AsyncImage is rendered with this contentDescription
        composeTestRule.onNodeWithContentDescription("Episode 1: The Journey's End").assertIsDisplayed()
    }

    /**
     * (e) EpisodeCard does NOT render an AsyncImage when episode.thumbnail == null.
     * The SeanimeSurface placeholder Box is shown instead; episode number Text remains present.
     * Verifies REQ-001 (no-thumbnail fallback, consistent 200×90dp dimensions).
     */
    @Test
    fun episodeCard_noImageNode_whenThumbnailAbsent() {
        val episodes = listOf(
            Episode(id = "ep1", number = 1, title = "The Journey's End", thumbnail = null)
        )
        val media = baseMedia()
        val vm = SeriesDetailViewModel(fakeRepo(media), fakeEngine(episodes = episodes))
        composeTestRule.setContent {
            SeanimeTVTheme {
                SeriesDetailScreen(viewModel = vm, anilistId = 1)
            }
        }
        // No AsyncImage node (no thumbnail contentDescription)
        composeTestRule.onNodeWithContentDescription("Episode 1: The Journey's End").assertDoesNotExist()
        // But episode number text is still present
        composeTestRule.onNodeWithText("Ep. 1").assertIsDisplayed()
    }

    /**
     * (f) TrailerOverlay composable is in the composition tree when trailerVisible == true.
     * Verified via the hint text that only exists inside TrailerOverlay.
     * Requires media.trailer.id != null so the overlay actually renders.
     * Verifies REQ-006.
     */
    @Test
    fun trailerOverlay_inComposition_whenTrailerVisibleIsTrue() {
        val media = baseMedia(trailer = AniListTrailer(site = "youtube", id = "abc123"))
        val vm = SeriesDetailViewModel(fakeRepo(media), fakeEngine())
        composeTestRule.setContent {
            SeanimeTVTheme {
                SeriesDetailScreen(viewModel = vm, anilistId = 1)
            }
        }
        // Initially the overlay is not shown
        composeTestRule.onNodeWithText("D-pad BACK  →  closes trailer").assertDoesNotExist()

        // Show the trailer overlay
        vm.showTrailer()
        composeTestRule.waitForIdle()

        // Overlay hint text is now in the composition
        composeTestRule.onNodeWithText("D-pad BACK  →  closes trailer").assertIsDisplayed()
    }
}
