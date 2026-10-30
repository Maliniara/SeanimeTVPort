package com.malina.seanimetvport.feature.player

import com.malina.seanimetvport.data.anilist.model.AniListMedia
import com.malina.seanimetvport.data.anilist.model.AniListMediaListEntry
import com.malina.seanimetvport.data.anilist.model.AniListUser
import com.malina.seanimetvport.data.anilist.model.MediaListStatus
import androidx.media3.common.MimeTypes
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
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
    // subtitleMimeType — picks the parser for a sideloaded subtitle track
    // -------------------------------------------------------------------------

    /** Plain extensions map to their own parser; anything else falls back to WebVTT. */
    @Test
    fun subtitleMimeType_mapsPlainExtensions() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("https://cdn/sub.srt"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("https://cdn/sub.ass"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("https://cdn/sub.ssa"))
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType("https://cdn/sub.vtt"))
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType("https://cdn/subs/12345"))
    }

    /**
     * Regression: CDN subtitle links carry query strings and fragments. Matching
     * the whole URL made these miss their extension and fall back to WebVTT, whose
     * parser rejects SubRip/SSA — the track then produced no cues at all.
     */
    @Test
    fun subtitleMimeType_ignoresQueryAndFragment() {
        assertEquals(
            MimeTypes.APPLICATION_SUBRIP,
            subtitleMimeType("https://cdn/sub.srt?token=abc&expires=123")
        )
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("https://cdn/sub.ass?token=abc"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("https://cdn/sub.SRT?x=1"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("https://cdn/sub.srt#frag"))
    }

    // -------------------------------------------------------------------------
    // End zone — the final PlayerViewModel.END_ZONE_MS counts as "watched"
    // -------------------------------------------------------------------------

    /**
     * Regression: a short episode must NOT be marked watched just because it is
     * within an absolute 2 minutes of the end. A sub-2-minute episode satisfies
     * `duration - position <= 120_000` at position 0, so an absolute end-zone rule
     * marked it watched before a single frame played. Only the relative 80 % rule
     * decides "watched".
     */
    @Test
    fun shortEpisodeAtStart_doesNotSave() = runTest {
        val recordingRepo = RecordingAniListRepository()
        val viewModel = PlayerViewModel(recordingRepo, OkHttpClient())
        viewModel.initTestState(mediaId = 7, episodeNumber = 3)

        // 90-second episode, playhead at zero: inside an absolute 2-minute window.
        viewModel.checkProgressInternal(position = 0L, duration = 90_000L)
        // 5-minute episode at 3:20 — 67 % in, also inside an absolute 2-minute window.
        viewModel.checkProgressInternal(position = 200_000L, duration = 300_000L)
        advanceUntilIdle()

        assertEquals(
            "below 80% must never count as watched",
            0, recordingRepo.saveProgressCalls.size
        )
    }

    /** The end-zone window is capped at 20 % of runtime, so it can't swallow a short episode. */
    @Test
    fun isInEndZone_capsWindowRelativeToDuration() {
        // 24-minute episode: the full 2-minute window applies.
        assertTrue(isInEndZone(position = 1_330_000L, duration = 1_440_000L))
        assertFalse(isInEndZone(position = 1_000_000L, duration = 1_440_000L))
        // 90-second episode: an absolute 2-minute window would be true at zero.
        assertFalse(isInEndZone(position = 0L, duration = 90_000L))
        assertTrue(isInEndZone(position = 85_000L, duration = 90_000L))
        assertFalse(isInEndZone(position = 0L, duration = 0L))
    }

    /** Mid-episode is neither at 80 % nor in the end zone, so nothing is saved. */
    @Test
    fun progressMidEpisode_doesNotSave() = runTest {
        val recordingRepo = RecordingAniListRepository()
        val viewModel = PlayerViewModel(recordingRepo, OkHttpClient())
        viewModel.initTestState(mediaId = 7, episodeNumber = 3)

        // 24-minute episode at ~11:40 — 49 % in, far outside the final 2 minutes.
        viewModel.checkProgressInternal(position = 700_000L, duration = 1_440_000L)
        advanceUntilIdle()

        assertEquals("mid-episode must not save", 0, recordingRepo.saveProgressCalls.size)
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
