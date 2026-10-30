package com.malina.seanimetvport.feature.home

import com.malina.seanimetvport.data.anilist.model.AniListCoverImage
import com.malina.seanimetvport.data.anilist.model.AniListMedia
import com.malina.seanimetvport.data.anilist.model.AniListMediaListEntry
import com.malina.seanimetvport.data.anilist.model.AniListTitle
import com.malina.seanimetvport.data.anilist.model.AniListUser
import com.malina.seanimetvport.data.anilist.model.MediaListStatus
import com.malina.seanimetvport.data.anilist.repository.FakeAniListRepository
import com.malina.seanimetvport.data.history.WatchHistoryEntry
import com.malina.seanimetvport.data.model.AnimeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeEntry(mediaId: Int, englishTitle: String, coverUrl: String = "") =
        AniListMediaListEntry(
            id = mediaId,
            progress = 0,
            status = "CURRENT",
            media = AniListMedia(
                id = mediaId,
                title = AniListTitle(english = englishTitle),
                coverImage = AniListCoverImage(large = coverUrl)
            )
        )

    /** Both currentList and planningList are populated when both statuses return data. */
    @Test
    fun shouldShowTwoSections_whenSuccess() = runTest {
        val currentEntries = listOf(makeEntry(1, "Cowboy Bebop", "http://img/cb.jpg"))
        val planningEntries = listOf(makeEntry(2, "One Piece", "http://img/op.jpg"))
        val fakeRepo = FakeAniListRepository(
            viewerResult = Result.success(AniListUser(id = 1, name = "user")),
            mediaListByStatus = mapOf(
                MediaListStatus.CURRENT to Result.success(currentEntries),
                MediaListStatus.PLANNING to Result.success(planningEntries)
            )
        )
        val viewModel = HomeViewModel(fakeRepo)
        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success, got: $state", state is HomeUiState.Success)
        val success = state as HomeUiState.Success
        assertEquals(1, success.currentList.size)
        assertEquals("Cowboy Bebop", success.currentList[0].title)
        assertEquals("http://img/cb.jpg", success.currentList[0].coverImageUrl)
        assertEquals(1, success.planningList.size)
        assertEquals("One Piece", success.planningList[0].title)
    }

    /** Both CURRENT and PLANNING getMediaList calls are made and both lists are populated. */
    @Test
    fun shouldRunCurrentAndPlanningInParallel() = runTest {
        val currentEntries = listOf(makeEntry(10, "Attack on Titan"))
        val planningEntries = listOf(makeEntry(20, "Steins;Gate"))
        val fakeRepo = FakeAniListRepository(
            viewerResult = Result.success(AniListUser(id = 42, name = "user")),
            mediaListByStatus = mapOf(
                MediaListStatus.CURRENT to Result.success(currentEntries),
                MediaListStatus.PLANNING to Result.success(planningEntries)
            )
        )
        val viewModel = HomeViewModel(fakeRepo)
        viewModel.loadHomeData()
        advanceUntilIdle()

        // The fake doesn't implement getMediaListCollection, so the ViewModel
        // falls back to per-status calls — all five statuses must be issued.
        val statuses = fakeRepo.mediaListCallArgs.map { it.second }
        assertTrue("Expected CURRENT request", statuses.contains(MediaListStatus.CURRENT))
        assertTrue("Expected PLANNING request", statuses.contains(MediaListStatus.PLANNING))
        assertEquals("Expected one getMediaList call per status", 5, fakeRepo.mediaListCallArgs.size)

        val success = viewModel.uiState.value as HomeUiState.Success
        assertEquals(1, success.currentList.size)
        assertEquals(1, success.planningList.size)
    }

    /** Viewer failure transitions directly to Error without calling getMediaList. */
    @Test
    fun shouldShowError_whenGetViewerFails() = runTest {
        val fakeRepo = FakeAniListRepository(
            viewerResult = Result.failure(RuntimeException("Network error"))
        )
        val viewModel = HomeViewModel(fakeRepo)
        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error, got: $state", state is HomeUiState.Error)
        assertEquals("Network error", (state as HomeUiState.Error).message)
        assertEquals("getMediaList must not be called on viewer failure",
            0, fakeRepo.mediaListCallArgs.size)
    }

    /** ViewModel starts in Loading state before any load is triggered. */
    @Test
    fun shouldShowLoading_initially() = runTest {
        val viewModel = HomeViewModel(FakeAniListRepository())
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
    }

    // -------------------------------------------------------------------------
    // Home card context menu (long-press) actions
    // -------------------------------------------------------------------------

    /** changeStatus forwards the media id, current progress and new status to AniList. */
    @Test
    fun changeStatus_savesNewStatusKeepingProgress() = runTest {
        val fakeRepo = FakeAniListRepository()
        val viewModel = HomeViewModel(fakeRepo)

        viewModel.changeStatus(mediaId = 42, progress = 7, status = MediaListStatus.DROPPED)
        advanceUntilIdle()

        assertEquals("saveProgress should be called once", 1, fakeRepo.saveProgressCallArgs.size)
        assertEquals(
            Triple(42, 7, MediaListStatus.DROPPED),
            fakeRepo.saveProgressCallArgs.first()
        )
    }

    /**
     * Hiding a series from Continue Watching is a local view filter only — it
     * records the id and must NOT touch the AniList list (that is the separate
     * "change status" action).
     */
    @Test
    fun dismissFromContinue_hidesLocallyWithoutTouchingAniList() = runTest {
        val fakeRepo = FakeAniListRepository()
        val viewModel = HomeViewModel(fakeRepo)

        viewModel.dismissFromContinue(anilistId = 99)
        advanceUntilIdle()

        assertTrue("id should be recorded as dismissed", 99 in viewModel.dismissedContinue.value)
        assertEquals(
            "dismissing must not write to AniList",
            0, fakeRepo.saveProgressCallArgs.size
        )
    }

    private fun historyOf(anilistId: Int, episode: Int) = WatchHistoryEntry(
        anilistId = anilistId,
        title = "t",
        coverImageUrl = "",
        episodeNumber = episode,
        positionMs = 720_000,
        durationMs = 1_440_000,
        updatedAt = 0
    )

    private fun successWith(current: List<AnimeEntry>) = HomeUiState.Success(
        currentList = current,
        planningList = emptyList(),
        completedList = emptyList(),
        droppedList = emptyList(),
        pausedList = emptyList()
    )

    /** History for an episode AniList already counts as watched is stale. */
    @Test
    fun staleHistoryIds_whenAniListProgressReachesEpisode_returnsSeries() {
        val state = successWith(listOf(AnimeEntry(id = 1, title = "Dr. Stone", coverImageUrl = "", progress = 3)))

        val stale = staleHistoryIds(listOf(historyOf(anilistId = 1, episode = 3)), state)

        assertEquals(listOf(1), stale)
    }

    /** History ahead of AniList progress is a genuine resume point and is kept. */
    @Test
    fun staleHistoryIds_whenAniListProgressBehindEpisode_keepsSeries() {
        val state = successWith(listOf(AnimeEntry(id = 1, title = "Dr. Stone", coverImageUrl = "", progress = 2)))

        val stale = staleHistoryIds(listOf(historyOf(anilistId = 1, episode = 3)), state)

        assertTrue(stale.isEmpty())
    }

    /** A series that is not on the AniList lists keeps its local history. */
    @Test
    fun staleHistoryIds_whenSeriesNotOnAniList_keepsSeries() {
        val state = successWith(emptyList())

        val stale = staleHistoryIds(listOf(historyOf(anilistId = 1, episode = 3)), state)

        assertTrue(stale.isEmpty())
    }

    /** Dismissals accumulate so several cards can be hidden in one session. */
    @Test
    fun dismissFromContinue_accumulatesMultipleIds() = runTest {
        val viewModel = HomeViewModel(FakeAniListRepository())

        viewModel.dismissFromContinue(1)
        viewModel.dismissFromContinue(2)
        advanceUntilIdle()

        assertEquals(setOf(1, 2), viewModel.dismissedContinue.value)
    }
}
