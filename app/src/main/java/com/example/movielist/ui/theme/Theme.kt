package com.example.movielist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MovieColorScheme =
    darkColorScheme(
        primary = MovieRed,
        onPrimary = Color.White,
        background = MovieBackground,
        onBackground = Color.White,
        surface = MovieSurface,
        onSurface = Color.White,
    )

@Composable
fun MovieListTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MovieColorScheme, typography = MovieTypography, content = content)
}
