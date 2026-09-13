package com.example.movielist.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movielist.R
import com.example.movielist.ui.details.MovieDetailsScreen
import com.example.movielist.ui.lists.MovieListsScreen
import com.example.movielist.ui.matches.MatchPopup
import com.example.movielist.ui.matches.MatchesScreen
import com.example.movielist.ui.navigation.AppDrawer
import com.example.movielist.ui.navigation.AppSection
import com.example.movielist.ui.user.UserScreen
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(factory: ViewModelProvider.Factory) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }

    BackHandler(enabled = state.selectedMovie != null) { viewModel.closeDetails() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    AppDrawer(
        drawerState = drawerState,
        currentSection = state.currentSection,
        onSectionChange = viewModel::selectSection,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                val selection = state.selectedMovie
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
                    selection != null ->
                        MovieDetailsScreen(
                            category = selection.category.displayName,
                            movie = selection.movie,
                            isLiked = selection.movie.id in state.currentUser.likedMovieIds,
                            isDisliked = selection.movie.id in state.currentUser.dislikedMovieIds,
                            onBack = viewModel::closeDetails,
                            onLike = { viewModel.rateSelectedMovie(liked = true) },
                            onDislike = { viewModel.rateSelectedMovie(liked = false) },
                        )
                    else ->
                        when (state.currentSection) {
                            AppSection.LISTS ->
                                MovieListsScreen(
                                    likedMovies = state.likedMovies,
                                    dislikedMovies = state.dislikedMovies,
                                    selectedFilter = state.listFilter,
                                    onFilterChange = viewModel::selectFilter,
                                    onMenuClick = openMenu,
                                    onMovieClick = { movie, filter ->
                                        viewModel.selectMovie(filter.category, movie)
                                    },
                                )
                            AppSection.MATCHES -> MatchesScreen(state.matches, openMenu)
                            AppSection.USER ->
                                UserScreen(
                                    users = state.users,
                                    selectedUserIndex = state.selectedUserIndex,
                                    onUserSelected = viewModel::selectUser,
                                    onMenuClick = openMenu,
                                )
                            AppSection.HOME ->
                                if (state.movies.isEmpty()) {
                                    LoadingMessage(
                                        stringResource(R.string.no_movies_available),
                                        viewModel::loadMovies,
                                    )
                                } else {
                                    HomeContent(
                                        categoryMovies = state.categoryMovies,
                                        categoryIndexes = state.categoryIndexes,
                                        onMenuClick = openMenu,
                                        onMovieClick = viewModel::selectMovie,
                                        onLike = { viewModel.rateCategoryMovie(it, liked = true) },
                                        onDislike = {
                                            viewModel.rateCategoryMovie(it, liked = false)
                                        },
                                    )
                                }
                        }
                }

                state.matchPopup?.let { match -> MatchPopup(match, viewModel::dismissMatch) }
            }
        }
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
