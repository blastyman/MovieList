package com.example.movielist.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.movielist.data.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.ui.components.RecommendedMovieCard

@Composable
fun HomeContent(
    categoryMovies: Map<MovieCategory, List<Movie>>,
    categoryIndexes: SnapshotStateMap<MovieCategory, Int>,
    onMenuClick: () -> Unit,
    onMovieClick: (MovieCategory, Movie) -> Unit,
    onLike: (MovieCategory) -> Unit,
    onDislike: (MovieCategory) -> Unit
) {
    val recommendedMovies = categoryMovies[MovieCategory.RECOMMENDED].orEmpty()
    if (recommendedMovies.isEmpty()) return

    val recommendedIndex = categoryIndexes[MovieCategory.RECOMMENDED] ?: 0
    val recommendedMovie = recommendedMovies[recommendedIndex % recommendedMovies.size]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }

            Text(
                text = "movielist",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                ),
                color = Color(0xFFE50914),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MovieCategory.RECOMMENDED.icon,
                contentDescription = null,
                tint = Color(0xFFE50914),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "recommended for you",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        RecommendedMovieCard(
            movie = recommendedMovie,
            onClick = { onMovieClick(MovieCategory.RECOMMENDED, recommendedMovie) },
            onLike = { onLike(MovieCategory.RECOMMENDED) },
            onDislike = { onDislike(MovieCategory.RECOMMENDED) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        MovieSection(MovieCategory.POPULAR, categoryMovies[MovieCategory.POPULAR].orEmpty(), onMovieClick)
        MovieSection(MovieCategory.COMEDY, categoryMovies[MovieCategory.COMEDY].orEmpty(), onMovieClick)
        MovieSection(MovieCategory.ACTION, categoryMovies[MovieCategory.ACTION].orEmpty(), onMovieClick)
        MovieSection(MovieCategory.ROMANCE, categoryMovies[MovieCategory.ROMANCE].orEmpty(), onMovieClick)
        MovieSection(MovieCategory.SCIFI, categoryMovies[MovieCategory.SCIFI].orEmpty(), onMovieClick)
    }
}