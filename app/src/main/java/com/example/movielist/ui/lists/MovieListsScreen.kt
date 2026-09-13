package com.example.movielist.ui.lists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.movielist.domain.model.Movie
import com.example.movielist.ui.components.MoviePosterItem
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.components.icon

@Composable
fun MovieListsScreen(
    likedMovies: List<Movie>,
    dislikedMovies: List<Movie>,
    selectedFilter: MovieListFilter,
    onFilterChange: (MovieListFilter) -> Unit,
    onMenuClick: () -> Unit,
    onMovieClick: (Movie, MovieListFilter) -> Unit,
) {
    val movies =
        when (selectedFilter) {
            MovieListFilter.LIKED -> likedMovies
            MovieListFilter.DISLIKED -> dislikedMovies
        }

    Column(
        modifier =
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenHeader("lists", onMenuClick)
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        ) {
            MovieListFilter.entries.forEach { filter ->
                FilterButton(filter, filter == selectedFilter) { onFilterChange(filter) }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        if (movies.isEmpty()) {
            Text("No movies here yet.", color = Color.LightGray)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(movies, key = Movie::id) { movie ->
                    MoviePosterItem(movie, onClick = { onMovieClick(movie, selectedFilter) })
                }
            }
        }
    }
}

@Composable
private fun FilterButton(filter: MovieListFilter, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val foreground = if (selected) Color.White else Color.Gray
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) primary else Color.Gray),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = if (selected) primary else Color.Transparent,
                contentColor = foreground,
            ),
    ) {
        Icon(imageVector = filter.category.icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(filter.category.displayName)
    }
}
