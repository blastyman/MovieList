package com.example.movielist.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.movielist.R
import com.example.movielist.domain.model.Reaction
import com.example.movielist.ui.details.MovieDetailsScreen
import com.example.movielist.ui.lists.MovieListsScreen
import com.example.movielist.ui.matches.MatchesScreen
import com.example.movielist.ui.navigation.AppDrawer
import com.example.movielist.ui.navigation.AppSection
import com.example.movielist.ui.user.UserScreen
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    factory: ViewModelProvider.Factory,
    viewModelKey: String,
    accountUsername: String?,
    onLogout: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(key = viewModelKey, factory = factory)
    val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentSection = AppSection.fromRoute(backStackEntry?.destination?.route) ?: AppSection.HOME
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val snackbarHost = remember { SnackbarHostState() }

    // A like or rating that failed to save was undone; tell the user once.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    AppDrawer(
        drawerState = drawerState,
        currentSection = currentSection,
        onSectionChange = { section ->
            viewModel.closeDetails()
            navController.navigateToSection(section)
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    state.errorMessage != null ->
                        LoadingMessage(
                            message =
                                stringResource(R.string.error_loading_movies, state.errorMessage),
                            onRetry = viewModel::loadMovies,
                        )
                    else ->
                        SectionNavHost(
                            navController = navController,
                            state = state,
                            viewModel = viewModel,
                            accountUsername = accountUsername,
                            onLogout = onLogout,
                            openMenu = openMenu,
                        )
                }

                // Details sit on top of the current section, so the section keeps its scroll
                // position underneath and like/dislike can step through the category.
                state.selectedMovie?.let { selection ->
                    // The newest back handler wins. Composing this one only while details are
                    // shown puts it after NavHost's, so Back closes details before leaving a section.
                    BackHandler { viewModel.closeDetails() }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MovieDetailsScreen(
                            category = selection.category.displayName,
                            movie = selection.movie,
                            isLiked = state.reactionTo(selection.movie.id) == Reaction.LIKE,
                            isDisliked = state.reactionTo(selection.movie.id) == Reaction.DISLIKE,
                            rating = state.selectedRating,
                            onBack = viewModel::closeDetails,
                            onLike = { viewModel.rateSelectedMovie(liked = true) },
                            onDislike = { viewModel.rateSelectedMovie(liked = false) },
                            onRate = viewModel::setScore,
                        )
                    }
                }

                SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())

                if (drawerState.isOpen) BackHandler { scope.launch { drawerState.close() } }
            }
        }
    }
}

@Composable
private fun SectionNavHost(
    navController: NavHostController,
    state: HomeUiState,
    viewModel: HomeViewModel,
    accountUsername: String?,
    onLogout: () -> Unit,
    openMenu: () -> Unit,
) {
    NavHost(navController = navController, startDestination = AppSection.HOME.route) {
        composable(AppSection.HOME.route) {
            if (state.movies.isEmpty()) {
                LoadingMessage(stringResource(R.string.no_movies_available), viewModel::loadMovies)
            } else {
                HomeContent(
                    categoryMovies = state.categoryMovies,
                    categoryIndexes = state.categoryIndexes,
                    onMenuClick = openMenu,
                    onMovieClick = viewModel::selectMovie,
                    onLike = { viewModel.rateCategoryMovie(it, liked = true) },
                    onDislike = { viewModel.rateCategoryMovie(it, liked = false) },
                )
            }
        }
        composable(AppSection.LISTS.route) {
            MovieListsScreen(
                likedMovies = state.likedMovies,
                dislikedMovies = state.dislikedMovies,
                selectedFilter = state.listFilter,
                onFilterChange = viewModel::selectFilter,
                onMenuClick = openMenu,
                onMovieClick = { movie, filter -> viewModel.selectMovie(filter.category, movie) },
            )
        }
        composable(AppSection.MATCHES.route) { MatchesScreen(openMenu) }
        composable(AppSection.USER.route) {
            UserScreen(
                accountUsername = accountUsername,
                onLogout = onLogout,
                onMenuClick = openMenu,
            )
        }
    }
}

/**
 * Drawer navigation: one copy of each section on the back stack, above home. Back from any
 * section returns to home; back from home leaves the app.
 */
private fun NavHostController.navigateToSection(section: AppSection) {
    navigate(section.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun LoadingMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(message)
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
