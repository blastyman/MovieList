package com.example.movielist.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.movielist.ui.theme.MovieListTheme
import org.junit.Rule
import org.junit.Test

class ScreenHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersTitle() {
        composeRule.setContent {
            MovieListTheme { ScreenHeader(title = "movies", onMenuClick = {}) }
        }

        composeRule.onNodeWithText("movies").assertIsDisplayed().assertTextEquals("movies")
    }
}
