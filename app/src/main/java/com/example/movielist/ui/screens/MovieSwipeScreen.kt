package com.example.movielist.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.movielist.data.model.Movie
import com.example.movielist.data.repository.MovieRepository
import com.example.movielist.domain.AppUser
import com.example.movielist.domain.MovieGroup

@Composable
fun MovieSwipeScreen(token: String) {
    val repository = remember { MovieRepository() }

    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showMatches by remember { mutableStateOf(false) }

    val currentUser = remember { AppUser("User 1") }
    val group = remember {
        MovieGroup().apply {
            users.add(currentUser)
        }
    }

    LaunchedEffect(Unit) {
        try {
            movies = repository.getMovies(token)
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message
            isLoading = false
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        errorMessage != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: $errorMessage")
            }
        }

        showMatches || currentIndex >= movies.size -> {
            MatchesScreen(
                matchedMovies = group.getMatches(movies),
                onBackToMovies = {
                    showMatches = false
                    currentIndex = 0
                    currentUser.likedMovies.clear()
                }
            )
        }

        else -> {
            val movie = movies[currentIndex]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                        contentDescription = movie.title,
                        modifier = Modifier
                            .height(420.dp)
                            .fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = movie.overview,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            currentIndex++
                        }
                    ) {
                        Text("Dislike")
                    }

                    Button(
                        onClick = {
                            currentUser.likedMovies.add(movie.id)
                            currentIndex++
                        }
                    ) {
                        Text("Like")
                    }
                }
            }
        }
    }
}