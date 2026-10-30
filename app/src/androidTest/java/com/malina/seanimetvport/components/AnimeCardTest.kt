package com.malina.seanimetvport.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.malina.seanimetvport.ui.theme.SeanimeTVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Test 6.1a: AnimeCard without progress does not render a LinearProgressIndicator
    @Test
    fun animeCard_noProgress_doesNotShowProgressIndicator() {
        composeTestRule.setContent {
            SeanimeTVTheme {
                AnimeCard(
                    title = "Test Anime",
                    coverImageUrl = "",
                    onClick = {},
                    showProgress = false
                )
            }
        }
        composeTestRule.onNodeWithTag("progress_indicator").assertDoesNotExist()
    }

    // Test 6.1b: AnimeCard with showProgress=true renders a LinearProgressIndicator
    @Test
    fun animeCard_withProgress_showsProgressIndicator() {
        composeTestRule.setContent {
            SeanimeTVTheme {
                AnimeCard(
                    title = "Test Anime",
                    coverImageUrl = "",
                    onClick = {},
                    showProgress = true,
                    progressFraction = 0.68f
                )
            }
        }
        composeTestRule.onNodeWithTag("progress_indicator").assertIsDisplayed()
    }

    // Test 6.1c: AnimeCard renders the title text
    @Test
    fun animeCard_displaysTitle() {
        composeTestRule.setContent {
            SeanimeTVTheme {
                AnimeCard(
                    title = "Cowboy Bebop",
                    coverImageUrl = "",
                    onClick = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Cowboy Bebop").assertIsDisplayed()
    }
}
