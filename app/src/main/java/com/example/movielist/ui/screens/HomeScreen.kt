package com.example.movielist.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch

data class SelectedMovieState(
    val category: MovieCategory,
    val movie: Movie
)

@Composable
fun HomeScreen(token: String) {
    val repository = remember { MovieRepository() }

    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMovieState by remember { mutableStateOf<SelectedMovieState?>(null) }
    var currentSection by remember { mutableStateOf(AppSection.HOME) }
    var selectedListFilter by remember { mutableStateOf(MovieListFilter.LIKED) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentUser = remember { AppUser("User 1") }
    val dislikedMovies = remember { mutableStateListOf<Int>() }

    val categoryIndexes = remember {
        mutableStateMapOf(
            MovieCategory.RECOMMENDED to 0,
            MovieCategory.POPULAR to 0,
            MovieCategory.COMEDY to 0,
            MovieCategory.ACTION to 0,
            MovieCategory.ROMANCE to 0,
            MovieCategory.SCIFI to 0
        )
    }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            movies = repository.getMovies(token = token)
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
            val usedMovieIds = mutableSetOf<Int>()

            fun moviesForCategory(category: MovieCategory): List<Movie> {
                val genreId = category.genreId ?: return emptyList()

                val filteredMovies = movies.filter { movie ->
                    genreId in movie.genre_ids && movie.id !in usedMovieIds
                }.shuffled()

                usedMovieIds.addAll(filteredMovies.map { it.id })
                return filteredMovies
            }

            mapOf(
                MovieCategory.RECOMMENDED to movies.shuffled(),
                MovieCategory.POPULAR to movies,
                MovieCategory.COMEDY to moviesForCategory(MovieCategory.COMEDY),
                MovieCategory.ACTION to moviesForCategory(MovieCategory.ACTION),
                MovieCategory.ROMANCE to moviesForCategory(MovieCategory.ROMANCE),
                MovieCategory.SCIFI to moviesForCategory(MovieCategory.SCIFI)
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(220.dp),
                drawerContainerColor = Color(0xFF141414)
            ) {
                Spacer(modifier = Modifier.height(36.dp))

                DrawerItem(
                    text = "home",
                    icon = Icons.Default.Home,
                    selected = currentSection == AppSection.HOME,
                    onClick = {
                        currentSection = AppSection.HOME
                        scope.launch { drawerState.close() }
                    }
                )

                DrawerItem(
                    text = "lists",
                    icon = Icons.Default.Folder,
                    selected = currentSection == AppSection.LISTS,
                    onClick = {
                        currentSection = AppSection.LISTS
                        scope.launch { drawerState.close() }
                    }
                )

                DrawerItem(
                    text = "matches",
                    icon = Icons.Default.Group,
                    selected = currentSection == AppSection.MATCHES,
                    onClick = {
                        currentSection = AppSection.MATCHES
                        scope.launch { drawerState.close() }
                    }
                )

                DrawerItem(
                    text = "user",
                    icon = Icons.Default.Person,
                    selected = currentSection == AppSection.USER,
                    onClick = {
                        currentSection = AppSection.USER
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101010)
        ) {
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

                currentSection == AppSection.LISTS -> {
                    MovieListsScreen(
                        likedMovies = movies.filter { it.id in currentUser.likedMovies },
                        dislikedMovies = movies.filter { it.id in dislikedMovies },
                        selectedFilter = selectedListFilter,
                        onFilterChange = { selectedListFilter = it },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onMovieClick = { movie, type ->
                            val category = if (type == "liked") MovieCategory.LIKED else MovieCategory.DISLIKED
                            selectedMovieState = SelectedMovieState(category, movie)
                        }
                    )
                }

                currentSection == AppSection.MATCHES -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Matches coming soon", color = Color.White)
                    }
                }

                currentSection == AppSection.USER -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "User profile coming soon", color = Color.White)
                    }
                }

                movies.isNotEmpty() -> {
                    HomeContent(
                        categoryMovies = categoryMovies,
                        categoryIndexes = categoryIndexes,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onMovieClick = { category, movie ->
                            selectedMovieState = SelectedMovieState(category, movie)
                        },
                        onLike = { category ->
                            val list = categoryMovies[category].orEmpty()

                            if (list.isNotEmpty()) {
                                val index = categoryIndexes[category] ?: 0
                                val movie = list[index % list.size]
                                currentUser.likedMovies.add(movie.id)
                                dislikedMovies.remove(movie.id)
                                categoryIndexes[category] = (index + 1) % list.size
                            }
                        },
                        onDislike = { category ->
                            val list = categoryMovies[category].orEmpty()

                            if (list.isNotEmpty()) {
                                val index = categoryIndexes[category] ?: 0
                                val movie = list[index % list.size]
                                currentUser.likedMovies.remove(movie.id)

                                if (movie.id !in dislikedMovies) {
                                    dislikedMovies.add(movie.id)
                                }

                                categoryIndexes[category] = (index + 1) % list.size
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerItem(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.LightGray
            )
        },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Default
                )
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFFE50914),
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = Color.White,
            unselectedTextColor = Color.LightGray
        )
    )
}

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