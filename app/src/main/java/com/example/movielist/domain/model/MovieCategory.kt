package com.example.movielist.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
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