package com.example.movielist.ui.lists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.movielist.data.model.Movie
import com.example.movielist.ui.components.MoviePosterItem

@Composable
fun MovieListsScreen(
    likedMovies: List<Movie>,
    dislikedMovies: List<Movie>,
    selectedFilter: MovieListFilter,
    onFilterChange: (MovieListFilter) -> Unit,
    onMenuClick: () -> Unit,
    onMovieClick: (Movie, String) -> Unit
) {

    val movies =
        when (selectedFilter) {

            MovieListFilter.LIKED ->
                likedMovies

            MovieListFilter.DISLIKED ->
                dislikedMovies
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {

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
                text = "lists",

                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    ),

                color = Color(0xFFE50914),

                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {

            OutlinedButton(
                onClick = {
                    onFilterChange(MovieListFilter.LIKED)
                },

                shape = RoundedCornerShape(12.dp),

                border = BorderStroke(
                    1.dp,

                    if (selectedFilter == MovieListFilter.LIKED)
                        Color(0xFFE50914)
                    else
                        Color.Gray
                ),

                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor =
                        if (selectedFilter == MovieListFilter.LIKED)
                            Color(0xFFE50914)
                        else
                            Color.Transparent
                )
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,

                        tint =
                            if (selectedFilter == MovieListFilter.LIKED)
                                Color.White
                            else
                                Color.Gray
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "liked",

                        color =
                            if (selectedFilter == MovieListFilter.LIKED)
                                Color.White
                            else
                                Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.padding(6.dp))

            OutlinedButton(
                onClick = {
                    onFilterChange(MovieListFilter.DISLIKED)
                },

                shape = RoundedCornerShape(12.dp),

                border = BorderStroke(
                    1.dp,

                    if (selectedFilter == MovieListFilter.DISLIKED)
                        Color(0xFFE50914)
                    else
                        Color.Gray
                ),

                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor =
                        if (selectedFilter == MovieListFilter.DISLIKED)
                            Color(0xFFE50914)
                        else
                            Color.Transparent
                )
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = null,

                        tint =
                            if (selectedFilter == MovieListFilter.DISLIKED)
                                Color.White
                            else
                                Color.Gray
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "disliked",

                        color =
                            if (selectedFilter == MovieListFilter.DISLIKED)
                                Color.White
                            else
                                Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (movies.isEmpty()) {

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "No movies here yet.",
                    color = Color.LightGray
                )
            }

        } else {

            movies.chunked(2).forEach { rowMovies ->

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    rowMovies.forEach { movie ->

                        MoviePosterItem(
                            movie = movie,

                            onClick = {

                                onMovieClick(
                                    movie,

                                    if (selectedFilter == MovieListFilter.LIKED)
                                        "liked"
                                    else
                                        "disliked"
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}