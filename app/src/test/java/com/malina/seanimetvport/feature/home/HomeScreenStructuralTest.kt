package com.malina.seanimetvport.feature.home

import com.malina.seanimetvport.data.anilist.repository.FakeAniListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Structural regression guard for the HomeScreen error branch.
 *
 * Verifies that the ViewModel reaches [HomeUiState.Error] when the repository
 * fails — which is the precondition for the error branch to render both the
 * Retry button and the Open Settings button.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenStructuralTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * shouldShowRetryButton_inErrorState
     *
     * When getViewer() fails the ViewModel transitions to [HomeUiState.Error]. The
     * HomeScreen error branch always renders the Retry button for any Error value.
     */
    @Test
    fun shouldShowRetryButton_inErrorState() = runTest {
        val fakeRepo = FakeAniListRepository(
            viewerResult = Result.failure(Exception("No server configured"))
        )
        val viewModel = HomeViewModel(fakeRepo)
        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(
            "Expected Error state so the Retry button is rendered, got: $state",
            state is HomeUiState.Error
        )
    }

    /**
     * shouldShowOpenSettingsButton_inErrorState
     *
     * Guard for the Open Settings button: the ViewModel transitions to Error when
     * getViewer() fails, and the HomeScreen error branch renders the button whenever
     * [HomeUiState.Error] is active. Verifies the message is non-blank.
     */
    @Test
    fun shouldShowOpenSettingsButton_inErrorState() = runTest {
        val errorMessage = "No server configured — go to Settings"
        val fakeRepo = FakeAniListRepository(
            viewerResult = Result.failure(Exception(errorMessage))
        )
        val viewModel = HomeViewModel(fakeRepo)
        viewModel.loadHomeData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(
            "Expected Error state so the Open Settings button is rendered, got: $state",
            state is HomeUiState.Error
        )
        assertTrue(
            "Error message must be non-blank so the error branch is active",
            (state as HomeUiState.Error).message.isNotBlank()
        )
    }
}
