package com.example.seanimetv.feature.home

import com.example.seanimetv.data.anilist.model.AniListCoverImage
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListTitle
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.FakeAniListRepository
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
}
