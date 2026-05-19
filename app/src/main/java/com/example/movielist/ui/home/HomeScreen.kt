package com.example.movielist.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.movielist.data.model.Movie
import com.example.movielist.data.repository.MovieRepository
import com.example.movielist.domain.model.AppUser
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.state.SelectedMovieState
import com.example.movielist.ui.details.MovieDetailsScreen
import com.example.movielist.ui.lists.MovieListFilter
import com.example.movielist.ui.lists.MovieListsScreen
import com.example.movielist.ui.navigation.AppDrawer
import com.example.movielist.ui.navigation.AppSection
import com.example.movielist.ui.user.UserScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(token: String) {
    val repository = remember { MovieRepository() }

    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMovieState by remember { mutableStateOf<SelectedMovieState?>(null) }
    var currentSection by remember { mutableStateOf(AppSection.HOME) }
    var selectedListFilter by remember { mutableStateOf(MovieListFilter.LIKED) }
    var matchPopup by remember { mutableStateOf<Match?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val users = remember {
        mutableStateListOf(
            AppUser("Tester 67"),
            AppUser("Tester 69")
        )
    }

    var selectedUserIndex by remember { mutableStateOf(0) }
    val currentUser = users[selectedUserIndex]

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

    val currentMatches = users
        .filter { it != currentUser }
        .flatMap { otherUser ->
            movies.filter { movie ->
                movie.id in currentUser.likedMovies && movie.id in otherUser.likedMovies
            }.map { movie ->
                Match(
                    userName = otherUser.name,
                    movieTitle = movie.title,
                    posterUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
                )
            }
        }
        .distinctBy { "${it.userName}-${it.movieTitle}" }

    fun checkForMatch(movie: Movie) {
        val matchedUser = users.firstOrNull { user ->
            user != currentUser && movie.id in user.likedMovies
        }

        if (matchedUser != null) {
            matchPopup = Match(
                userName = matchedUser.name,
                movieTitle = movie.title,
                posterUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
            )
        }
    }

    fun likeMovie(movie: Movie) {
        if (movie.id !in currentUser.likedMovies) {
            currentUser.likedMovies.add(movie.id)
        }

        currentUser.dislikedMovies.remove(movie.id)
        checkForMatch(movie)
    }

    fun dislikeMovie(movieId: Int) {
        currentUser.likedMovies.remove(movieId)

        if (movieId !in currentUser.dislikedMovies) {
            currentUser.dislikedMovies.add(movieId)
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

    AppDrawer(
        drawerState = drawerState,
        currentSection = currentSection,
        onSectionChange = { currentSection = it },
        scope = scope
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101010)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                            isDisliked = state.movie.id in currentUser.dislikedMovies,
                            onBack = { selectedMovieState = null },
                            onLike = {
                                likeMovie(state.movie)
                                moveToNextMovie(state.category)
                            },
                            onDislike = {
                                dislikeMovie(state.movie.id)
                                moveToNextMovie(state.category)
                            }
                        )
                    }

                    currentSection == AppSection.LISTS -> {
                        MovieListsScreen(
                            likedMovies = movies.filter { it.id in currentUser.likedMovies },
                            dislikedMovies = movies.filter { it.id in currentUser.dislikedMovies },
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
                        MatchesContent(
                            matches = currentMatches,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }

                    currentSection == AppSection.USER -> {
                        UserScreen(
                            users = users,
                            selectedUserIndex = selectedUserIndex,
                            onUserSelected = { selectedUserIndex = it },
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
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
                                    likeMovie(movie)
                                    categoryIndexes[category] = (index + 1) % list.size
                                }
                            },
                            onDislike = { category ->
                                val list = categoryMovies[category].orEmpty()

                                if (list.isNotEmpty()) {
                                    val index = categoryIndexes[category] ?: 0
                                    val movie = list[index % list.size]
                                    dislikeMovie(movie.id)
                                    categoryIndexes[category] = (index + 1) % list.size
                                }
                            }
                        )
                    }
                }

                matchPopup?.let { match ->
                    MatchPopup(
                        match = match,
                        onDismiss = { matchPopup = null }
                    )
                }
            }
        }
    }
}

@Composable
fun MatchPopup(
    match: Match,
    onDismiss: () -> Unit
) {
    LaunchedEffect(match) {
        delay(5000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE50914))
        ) {
            Column(
                modifier = Modifier.padding(34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MATCH!",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${match.userName} also likes this.",
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MatchesContent(
    matches: List<Match>,
    onMenuClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }

            Text(
                text = "matches",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                ),
                color = Color(0xFFE50914),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No matches yet.",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        } else {
            LazyColumn {
                items(matches) { match ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = match.posterUrl,
                                contentDescription = match.movieTitle,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = match.movieTitle,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "You and ${match.userName} both like this movie.",
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}