package com.example.seanimetv.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.anilist.model.AniListMediaListEntry
import com.example.seanimetv.data.anilist.model.AniListTitle
import com.example.seanimetv.data.anilist.model.AniListUser
import com.example.seanimetv.data.anilist.model.AiringScheduleEntry
import com.example.seanimetv.data.anilist.model.MediaListStatus
import com.example.seanimetv.data.anilist.repository.AniListRepository
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

// ---------------------------------------------------------------------------
// Fake repositories — no real HTTP calls in tests
// ---------------------------------------------------------------------------

private val fakeUser = AniListUser(id = 1, name = "Tester")

/** Suspends getViewer() indefinitely, keeping ViewModel in Loading state. */
private class SuspendingFakeRepository : AniListRepository {
    private val deferred = CompletableDeferred<Result<AniListUser>>()
    override suspend fun getViewer(): Result<AniListUser> = deferred.await()
    override suspend fun getMediaList(userId: Int, status: MediaListStatus): Result<List<AniListMediaListEntry>> =
        Result.success(emptyList())
    override suspend fun getMediaById(id: Int): Result<AniListMedia> =
        Result.failure(UnsupportedOperationException())
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus): Result<Unit> =
        Result.success(Unit)
}

/** getViewer() fails immediately with the provided message. */
private class ErrorFakeRepository(private val message: String) : AniListRepository {
    override suspend fun getViewer(): Result<AniListUser> = Result.failure(Exception(message))
    override suspend fun getMediaList(userId: Int, status: MediaListStatus): Result<List<AniListMediaListEntry>> =
        Result.success(emptyList())
    override suspend fun getMediaById(id: Int): Result<AniListMedia> =
        Result.failure(UnsupportedOperationException())
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus): Result<Unit> =
        Result.success(Unit)
}

/** Returns user + configurable media lists per status. */
private class SuccessFakeRepository(
    private val currentEntries: List<AniListMediaListEntry> = emptyList(),
    private val planningEntries: List<AniListMediaListEntry> = emptyList()
) : AniListRepository {
    override suspend fun getViewer(): Result<AniListUser> = Result.success(fakeUser)
    override suspend fun getMediaList(userId: Int, status: MediaListStatus): Result<List<AniListMediaListEntry>> =
        when (status) {
            MediaListStatus.CURRENT -> Result.success(currentEntries)
            MediaListStatus.PLANNING -> Result.success(planningEntries)
            else -> Result.success(emptyList())
        }
    override suspend fun getMediaById(id: Int): Result<AniListMedia> =
        Result.failure(UnsupportedOperationException())
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus): Result<Unit> =
        Result.success(Unit)
}

/** Counts each call to getViewer() so tests can verify retry triggers a new repository call. */
private class CountingErrorRepository : AniListRepository {
    val callCount = AtomicInteger(0)
    override suspend fun getViewer(): Result<AniListUser> {
        callCount.incrementAndGet()
        return Result.failure(Exception("Server unreachable"))
    }
    override suspend fun getMediaList(userId: Int, status: MediaListStatus): Result<List<AniListMediaListEntry>> =
        Result.success(emptyList())
    override suspend fun getMediaById(id: Int): Result<AniListMedia> =
        Result.failure(UnsupportedOperationException())
    override suspend fun saveProgress(mediaId: Int, progress: Int, status: MediaListStatus): Result<Unit> =
        Result.success(Unit)
}

// ---------------------------------------------------------------------------
// Helper builders
// ---------------------------------------------------------------------------

private fun mediaEntry(id: Int, title: String) = AniListMediaListEntry(
    id = id,
    progress = 0,
    status = "CURRENT",
    media = AniListMedia(id = id, title = AniListTitle(romaji = title, english = title))
)

// ---------------------------------------------------------------------------
// HomeScreen tests
// ---------------------------------------------------------------------------

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * When ViewModel is in Loading state (repository never resolves),
     * the screen shows a progress indicator — no content sections appear.
     */
    @Test
    fun homeScreen_loadingState_showsNoContent() {
        val repo = SuspendingFakeRepository()
        val viewModel = HomeViewModel(repo)
        composeTestRule.setContent {
            SeanimeTVTheme {
                HomeScreen(viewModel = viewModel, aniListRepository = repo)
            }
        }
        composeTestRule.onNodeWithText("Continue watching").assertDoesNotExist()
        composeTestRule.onNodeWithText("Planning").assertDoesNotExist()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    /**
     * When ViewModel emits HomeUiState.Error, the error message and a Retry button are displayed.
     */
    @Test
    fun homeScreen_errorState_showsErrorMessageAndRetryButton() {
        val errorMessage = "Could not reach server — check your connection"
        val repo = ErrorFakeRepository(errorMessage)
        val viewModel = HomeViewModel(repo)
        composeTestRule.setContent {
            SeanimeTVTheme {
                HomeScreen(viewModel = viewModel, aniListRepository = repo)
            }
        }
        viewModel.loadHomeData()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    /**
     * When ViewModel emits Success with a non-empty current list,
     * the "Continue watching" section label is displayed.
     */
    @Test
    fun homeScreen_successWithCurrentList_showsContinueWatchingLabel() {
        val repo = SuccessFakeRepository(
            currentEntries = listOf(mediaEntry(1, "Cowboy Bebop"))
        )
        val viewModel = HomeViewModel(repo)
        composeTestRule.setContent {
            SeanimeTVTheme {
                HomeScreen(viewModel = viewModel, aniListRepository = repo)
            }
        }
        viewModel.loadHomeData()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Continue watching").assertIsDisplayed()
    }

    /**
     * When ViewModel emits Success with no current list but a non-empty planning list,
     * the "Planning" section row label is visible and "Continue watching" is absent.
     */
    @Test
    fun homeScreen_successWithPlanningOnly_showsPlanningSection() {
        val repo = SuccessFakeRepository(
            planningEntries = listOf(mediaEntry(2, "Trigun"))
        )
        val viewModel = HomeViewModel(repo)
        composeTestRule.setContent {
            SeanimeTVTheme {
                HomeScreen(viewModel = viewModel, aniListRepository = repo)
            }
        }
        viewModel.loadHomeData()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Planning").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue watching").assertDoesNotExist()
    }

    /**
     * Tapping Retry calls viewModel.retry(), which triggers a new repository call.
     * Verified via a counting fake that always errors — call count must be >= 2 after retry.
     */
    @Test
    fun homeScreen_retryButtonClick_triggersAnotherRepositoryCall() {
        val countingRepo = CountingErrorRepository()
        val viewModel = HomeViewModel(countingRepo)
        composeTestRule.setContent {
            SeanimeTVTheme {
                HomeScreen(viewModel = viewModel, aniListRepository = countingRepo)
            }
        }
        viewModel.loadHomeData()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Server unreachable").assertIsDisplayed()
        assert(countingRepo.callCount.get() >= 2) {
            "Expected >= 2 getViewer() calls after retry, got ${countingRepo.callCount.get()}"
        }
    }
}
