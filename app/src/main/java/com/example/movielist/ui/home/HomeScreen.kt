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
import com.example.movielist.ui.comments.CommentsSection
import com.example.movielist.ui.comments.CommentsViewModel
import com.example.movielist.ui.details.MovieDetailsScreen
import com.example.movielist.ui.lists.MovieListsScreen
import com.example.movielist.ui.matches.MatchesScreen
import com.example.movielist.ui.navigation.AppDrawer
import com.example.movielist.ui.navigation.AppSection
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.ui.profile.ProfileScreen
import com.example.movielist.ui.profile.ProfileViewModel
import com.example.movielist.ui.search.SearchScreen
import com.example.movielist.ui.search.SearchViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    factory: ViewModelProvider.Factory,
    commentsFactory: ViewModelProvider.Factory,
    searchFactory: ViewModelProvider.Factory,
    profileFactory: ViewModelProvider.Factory,
    viewModelKey: String,
    /** The signed-in account; null until it has loaded. */
    accountId: String?,
    onLogout: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(key = viewModelKey, factory = factory)
    val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
    val commentsViewModel: CommentsViewModel =
        viewModel(key = "$viewModelKey-comments", factory = commentsFactory)
    val comments = commentsViewModel.uiStateFlow.collectAsStateWithLifecycle().value
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
    LaunchedEffect(comments.message) {
        comments.message?.let {
            snackbarHost.showSnackbar(it)
            commentsViewModel.messageShown()
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
                            accountId = accountId,
                            searchFactory = searchFactory,
                            profileFactory = profileFactory,
                            onLogout = onLogout,
                            openMenu = openMenu,
                        )
                }

                // Details sit on top of the current section, so the section keeps its scroll
                // position underneath and like/dislike can step through the category.
                state.selectedMovie?.let { selection ->
                    LaunchedEffect(selection.movie.id) { commentsViewModel.show(selection.movie.id) }
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
                            trailer = state.selectedTrailer,
                            onBack = viewModel::closeDetails,
                            onLike = { viewModel.rateSelectedMovie(liked = true) },
                            onDislike = { viewModel.rateSelectedMovie(liked = false) },
                            onRate = viewModel::setScore,
                            footer = {
                                CommentsSection(
                                    state = comments,
                                    actions = commentsViewModel,
                                    myRating = state.selectedRating,
                                )
                            },
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
    accountId: String?,
    searchFactory: ViewModelProvider.Factory,
    profileFactory: ViewModelProvider.Factory,
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
        composable(AppSection.SEARCH.route) {
            val search: SearchViewModel = viewModel(factory = searchFactory)
            SearchScreen(
                state = search.uiStateFlow.collectAsStateWithLifecycle().value,
                onQueryChange = search::onQueryChange,
                onTabSelected = search::selectTab,
                onLoadMore = search::loadMore,
                onRetry = search::retry,
                onMovieClick = { movie -> viewModel.selectMovie(MovieCategory.SEARCH, movie) },
                onPersonClick = { person -> navController.navigate(profileRoute(person.id)) },
                onMenuClick = openMenu,
            )
        }
        composable(AppSection.USER.route) {
            if (accountId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                ProfileRoute(accountId, profileFactory, viewModel, onMenuClick = openMenu, onBack = null, onLogout = onLogout)
            }
        }
        composable(PROFILE_ROUTE) { entry ->
            val userId = entry.arguments?.getString(PROFILE_USER_ID).orEmpty()
            ProfileRoute(
                userId,
                profileFactory,
                viewModel,
                onMenuClick = null,
                onBack = { navController.popBackStack() },
                onLogout = onLogout,
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

/** Someone's profile, opened from search: `profile/<userId>`. Each opened profile has its own ViewModel. */
private const val PROFILE_USER_ID = "userId"
private const val PROFILE_ROUTE = "profile/{$PROFILE_USER_ID}"

private fun profileRoute(userId: String) = "profile/$userId"

@Composable
private fun ProfileRoute(
    userId: String,
    profileFactory: ViewModelProvider.Factory,
    homeViewModel: HomeViewModel,
    onMenuClick: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    val profile: ProfileViewModel = viewModel(factory = profileFactory)
    LaunchedEffect(userId) { profile.load(userId) }
    ProfileScreen(
        state = profile.uiStateFlow.collectAsStateWithLifecycle().value,
        onRetry = profile::retry,
        onMovieClick = { movie -> homeViewModel.selectMovie(MovieCategory.PROFILE, movie) },
        onMenuClick = onMenuClick,
        onBack = onBack,
        onEditName = profile::startEditName,
        onNameDraftChange = profile::onNameDraftChange,
        onSaveName = profile::saveName,
        onCancelEditName = profile::cancelEditName,
        onLogout = onLogout,
    )
}
