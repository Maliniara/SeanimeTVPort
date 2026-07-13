package com.example.seanimetv.feature.player

import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // isAt80Percent — pure function tests (no ExoPlayer needed)
    // -------------------------------------------------------------------------

    /** Returns true at exactly 80 %. */
    @Test
    fun isAt80Percent_returnsTrue_whenAtExact80Percent() {
        assertTrue(isAt80Percent(position = 80L, duration = 100L))
    }

    /** Returns false when below the 80 % threshold. */
    @Test
    fun isAt80Percent_returnsFalse_whenBelow80Percent() {
        assertFalse(isAt80Percent(position = 79L, duration = 100L))
    }

    /** Returns false when duration is zero (avoids divide-by-zero). */
    @Test
    fun isAt80Percent_returnsFalse_whenDurationIsZero() {
        assertFalse(isAt80Percent(position = 0L, duration = 0L))
    }

    // -------------------------------------------------------------------------
    // Progress save — ViewModel integration test
    // -------------------------------------------------------------------------

    /**
     * When playback reaches ≥ 80 %, [PlayerViewModel.checkProgressInternal] must call
     * [AniListRepository.saveProgress] exactly once with the correct media id and episode number.
     */
    @Test
    fun progressAt80Percent_triggersSave() = runTest {
        val recordingRepo = RecordingAniListRepository()
        val viewModel = PlayerViewModel(recordingRepo, OkHttpClient())
        viewModel.initTestState(mediaId = 42, episodeNumber = 5)

        viewModel.checkProgressInternal(position = 80L, duration = 100L)
        advanceUntilIdle()

        assertEquals("saveProgress should be called once", 1, recordingRepo.saveProgressCalls.size)
        val (savedMediaId, savedProgress, savedStatus) = recordingRepo.saveProgressCalls.first()
        assertEquals(42, savedMediaId)
        assertEquals(5, savedProgress)
        assertEquals(MediaListStatus.CURRENT, savedStatus)
    }

    /** A second call after the first save must not trigger another [AniListRepository.saveProgress]. */
    @Test
    fun progressAt80Percent_doesNotSaveTwice() = runTest {
        val recordingRepo = RecordingAniListRepository()
        val viewModel = PlayerViewModel(recordingRepo, OkHttpClient())
        viewModel.initTestState(mediaId = 1, episodeNumber = 1)

        viewModel.checkProgressInternal(position = 80L, duration = 100L)
        viewModel.checkProgressInternal(position = 90L, duration = 100L)
        advanceUntilIdle()

        assertEquals("saveProgress must fire only once", 1, recordingRepo.saveProgressCalls.size)
    }

    // -------------------------------------------------------------------------
    // Error state
    // -------------------------------------------------------------------------

    /**
     * When a playback error is encountered, the view-model must transition to
     * [PlayerUiState.Error] with the correct message.
     */
    @Test
    fun playerError_emitsErrorState() {
        val viewModel = PlayerViewModel(NoOpAniListRepository(), OkHttpClient())

        viewModel.simulateError("Playback error")

        val state = viewModel.uiState.value
        assertTrue("Expected PlayerUiState.Error", state is PlayerUiState.Error)
        assertEquals("Playback error", (state as PlayerUiState.Error).message)
    }
}

// ---------------------------------------------------------------------------
// Private test doubles — contained within this test file
// ---------------------------------------------------------------------------

/** Records every [saveProgress] call for assertion. */
private class RecordingAniListRepository : AniListRepository {
    val saveProgressCalls = mutableListOf<Triple<Int, Int, MediaListStatus>>()

    override suspend fun getViewer(): Result<AniListUser> =
        Result.success(AniListUser(id = 1, name = "test"))

    override suspend fun getMediaList(
        userId: Int,
        status: MediaListStatus
    ): Result<List<AniListMediaListEntry>> = Result.success(emptyList())

    override suspend fun getMediaById(id: Int): Result<AniListMedia> =
        Result.failure(UnsupportedOperationException())

    override suspend fun saveProgress(
        mediaId: Int,
        progress: Int,
        status: MediaListStatus
    ): Result<Unit> {
        saveProgressCalls.add(Triple(mediaId, progress, status))
        return Result.success(Unit)
    }
}

/** No-op fake for tests that only exercise non-save code paths. */
private class NoOpAniListRepository : AniListRepository {
    override suspend fun getViewer(): Result<AniListUser> =
        Result.success(AniListUser(id = 1, name = "test"))

    override suspend fun getMediaList(
        userId: Int,
        status: MediaListStatus
    ): Result<List<AniListMediaListEntry>> = Result.success(emptyList())

    override suspend fun getMediaById(id: Int): Result<AniListMedia> =
        Result.failure(UnsupportedOperationException())

    override suspend fun saveProgress(
        mediaId: Int,
        progress: Int,
        status: MediaListStatus
    ): Result<Unit> = Result.success(Unit)
}
