package com.malina.seanimetvport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the wired MainActivity.
 *
 * These tests launch the real Activity (not a standalone composable).  In the
 * test environment DataStore starts empty — no server URL is stored — so the
 * app reaches HomeUiState.Error with "No server configured — go to Settings"
 * rather than crashing.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Test 8.1a: Launching MainActivity with no server URL configured must not
     * crash.  The test simply observes the Activity launching successfully and
     * the "SEANIME" logo being rendered (the logo strip is always visible).
     */
    @Test
    fun mainActivity_launchesWithoutCrashing() {
        // If the Activity crashed during launch, the rule itself would fail.
        // Asserting the logo strip confirms the Compose tree was set up and
        // rendered to the screen.
        composeTestRule.onNodeWithText("SEANIME").assertIsDisplayed()
    }

    /**
     * Test 8.1b: With no server URL in DataStore, HomeViewModel reaches
     * HomeUiState.Error("No server configured — go to Settings").
     * The error message text must be visible on screen.
     */
    @Test
    fun mainActivity_noServerConfigured_showsErrorMessage() {
        composeTestRule.onNodeWithText("No server configured", substring = true)
            .assertIsDisplayed()
    }
}
