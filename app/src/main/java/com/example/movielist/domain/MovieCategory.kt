package com.example.movielist.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class MovieCategory(
    val displayName: String,
    val genreId: Int? = null,
    val icon: ImageVector
) {
    RECOMMENDED("recommended", null, Icons.Default.Star),
    POPULAR("popular", null, Icons.Default.LocalFireDepartment),
    COMEDY("comedy", 35, Icons.Default.EmojiEmotions),
    ACTION("action", 28, Icons.Default.Bolt),
    ROMANCE("romance", 10749, Icons.Default.Favorite),
    SCIFI("sci-fi", 878, Icons.Default.RocketLaunch),
    LIKED("liked", null, Icons.Default.ThumbUp),
    DISLIKED("disliked", null, Icons.Default.ThumbDown)
}