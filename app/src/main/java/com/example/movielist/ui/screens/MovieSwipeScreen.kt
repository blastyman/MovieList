package com.example.movielist.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.movielist.data.model.Movie
import com.example.movielist.data.repository.MovieRepository
import com.example.movielist.domain.AppUser
import com.example.movielist.domain.MovieCategory
import com.example.movielist.ui.components.MoviePosterItem
import com.example.movielist.ui.components.RecommendedMovieCard

data class SelectedMovieState(
    val category: MovieCategory,
    val movie: Movie
)

@Composable
fun MovieSwipeScreen(token: String) {
    val repository = remember { MovieRepository() }

    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMovieState by remember { mutableStateOf<SelectedMovieState?>(null) }

    val currentUser = remember { AppUser("User 1") }
    val dislikedMovies = remember { mutableStateListOf<Int>() }

    val categoryIndexes = remember {
        mutableStateMapOf(
            MovieCategory.RECOMMENDED to 0,
            MovieCategory.POPULAR to 0,
            MovieCategory.COMEDY to 0,
            MovieCategory.ACTION to 0,
            MovieCategory.ROMANCE to 0,
            MovieCategory.SCIFI to 0,
            MovieCategory.ANIME to 0
        )
    }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val randomPage = (1..20).random()
            movies = repository.getMovies(token = token, page = randomPage).shuffled()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message
            isLoading = false
        }
    }

    val categoryMovies = remember(movies) {
        if (movies.isEmpty()) {
            emptyMap()
        } else {
            mapOf(
                MovieCategory.RECOMMENDED to movies,
                MovieCategory.POPULAR to movies,
                MovieCategory.COMEDY to movies.shuffled(),
                MovieCategory.ACTION to movies.shuffled(),
                MovieCategory.ROMANCE to movies.shuffled(),
                MovieCategory.SCIFI to movies.shuffled(),
                MovieCategory.ANIME to movies.shuffled()
            )
        }
    }

    fun moveToNextMovie(category: MovieCategory) {
        val list = categoryMovies[category].orEmpty()
        if (list.isNotEmpty()) {
            val nextIndex = ((categoryIndexes[category] ?: 0) + 1) % list.size
            categoryIndexes[category] = nextIndex
            selectedMovieState = SelectedMovieState(category, list[nextIndex])
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101010)) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: $errorMessage", color = Color.White)
                }
            }

            selectedMovieState != null -> {
                val state = selectedMovieState!!

                MovieDetailsScreen(
                    category = state.category.displayName,
                    movie = state.movie,
                    isLiked = state.movie.id in currentUser.likedMovies,
                    isDisliked = state.movie.id in dislikedMovies,
                    onBack = { selectedMovieState = null },
                    onLike = {
                        currentUser.likedMovies.add(state.movie.id)
                        dislikedMovies.remove(state.movie.id)
                        moveToNextMovie(state.category)
                    },
                    onDislike = {
                        currentUser.likedMovies.remove(state.movie.id)
                        if (state.movie.id !in dislikedMovies) dislikedMovies.add(state.movie.id)
                        moveToNextMovie(state.category)
                    }
                )
            }

            movies.isNotEmpty() -> {
                HomeScreen(
                    categoryMovies = categoryMovies,
                    categoryIndexes = categoryIndexes,
                    onMovieClick = { category, movie -> selectedMovieState = SelectedMovieState(category, movie) },
                    onLike = { category ->
                        val list = categoryMovies[category].orEmpty()
                        val index = categoryIndexes[category] ?: 0
                        val movie = list[index % list.size]
                        currentUser.likedMovies.add(movie.id)
                        dislikedMovies.remove(movie.id)
                        categoryIndexes[category] = (index + 1) % list.size
                    },
                    onDislike = { category ->
                        val list = categoryMovies[category].orEmpty()
                        val index = categoryIndexes[category] ?: 0
                        val movie = list[index % list.size]
                        currentUser.likedMovies.remove(movie.id)
                        if (movie.id !in dislikedMovies) dislikedMovies.add(movie.id)
                        categoryIndexes[category] = (index + 1) % list.size
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    categoryMovies: Map<MovieCategory, List<Movie>>,
    categoryIndexes: Map<MovieCategory, Int>,
    onMovieClick: (MovieCategory, Movie) -> Unit,
    onLike: (MovieCategory) -> Unit,
    onDislike: (MovieCategory) -> Unit
) {
    val recommended = categoryMovies[MovieCategory.RECOMMENDED].orEmpty()
    val recommendedIndex = categoryIndexes[MovieCategory.RECOMMENDED] ?: 0
    val recommendedMovie = recommended[recommendedIndex % recommended.size]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
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

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Recommended for you",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

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
        MovieSection(MovieCategory.ANIME, categoryMovies[MovieCategory.ANIME].orEmpty(), onMovieClick)
    }
}

@Composable
fun MovieSection(
    category: MovieCategory,
    movies: List<Movie>,
    onMovieClick: (MovieCategory, Movie) -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = category.displayName,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White
    )

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