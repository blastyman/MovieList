package com.example.movielist.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.movielist.data.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.ui.components.MoviePosterItem

@Composable
fun MovieSection(
    category: MovieCategory,
    movies: List<Movie>,
    onMovieClick: (MovieCategory, Movie) -> Unit
) {
    if (movies.isEmpty()) return

    Spacer(modifier = Modifier.height(24.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = Color(0xFFE50914),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        movies.take(12).forEach { movie ->
            MoviePosterItem(
                movie = movie,
                onClick = { onMovieClick(category, movie) }
            )

            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}