package com.example.movielist.ui.components

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
import com.example.movielist.domain.model.MovieCategory

val MovieCategory.icon: ImageVector
    get() =
        when (this) {
            MovieCategory.RECOMMENDED -> Icons.Default.Star
            MovieCategory.POPULAR -> Icons.Default.LocalFireDepartment
            MovieCategory.COMEDY -> Icons.Default.EmojiEmotions
            MovieCategory.ACTION -> Icons.Default.Bolt
            MovieCategory.ROMANCE -> Icons.Default.Favorite
            MovieCategory.SCI_FI -> Icons.Default.RocketLaunch
            MovieCategory.LIKED -> Icons.Default.ThumbUp
            MovieCategory.DISLIKED -> Icons.Default.ThumbDown
        }
