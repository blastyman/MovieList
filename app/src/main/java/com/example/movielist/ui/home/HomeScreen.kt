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
import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.Avatar
import com.example.movielist.domain.model.Reaction
import com.example.movielist.ui.chat.ChatScreen
import com.example.movielist.ui.chat.ChatViewModel
import com.example.movielist.ui.chat.ChatsScreen
import com.example.movielist.ui.chat.ChatsViewModel
import com.example.movielist.ui.comments.CommentsSection
import com.example.movielist.ui.comments.CommentsViewModel
import com.example.movielist.ui.details.MovieDetailsScreen
import com.example.movielist.ui.lists.MovieListsScreen
import com.example.movielist.ui.matches.MatchPopup
import com.example.movielist.ui.matches.MatchesScreen
import com.example.movielist.ui.matches.MatchesViewModel
import com.example.movielist.ui.navigation.AppDrawer
import com.example.movielist.ui.navigation.AppSection
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.ui.friends.FriendsScreen
import com.example.movielist.ui.friends.FriendsViewModel
import com.example.movielist.ui.groups.CreateGroupScreen
import com.example.movielist.ui.groups.CreateGroupViewModel
import com.example.movielist.ui.groups.GroupActions
import com.example.movielist.ui.groups.GroupScreen
import com.example.movielist.ui.groups.GroupViewModel
import com.example.movielist.ui.groups.GroupsScreen
import com.example.movielist.ui.groups.GroupsViewModel
import com.example.movielist.ui.profile.ProfileFriendActions
import com.example.movielist.ui.profile.ProfileScreen
import com.example.movielist.ui.profile.ProfileViewModel
import com.example.movielist.ui.search.SearchScreen
import com.example.movielist.ui.search.SearchViewModel
import com.example.movielist.ui.settings.LocalAppSettings
import com.example.movielist.ui.settings.SettingsScreen
import com.example.movielist.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    factory: ViewModelProvider.Factory,
    commentsFactory: ViewModelProvider.Factory,
    searchFactory: ViewModelProvider.Factory,
    profileFactory: ViewModelProvider.Factory,
    friendsFactory: ViewModelProvider.Factory,
    matchesFactory: ViewModelProvider.Factory,
    groupsFactory: ViewModelProvider.Factory,
    chatFactory: ViewModelProvider.Factory,
    settingsFactory: ViewModelProvider.Factory,
    viewModelKey: String,
    /** The signed-in account; null until it has loaded. */
    account: Account?,
    onLogout: () -> Unit,
) {
    val accountId = account?.id
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
                            friendsFactory = friendsFactory,
                            matchesFactory = matchesFactory,
                            groupsFactory = groupsFactory,
                            chatFactory = chatFactory,
                            settingsFactory = settingsFactory,
                            account = account,
                            snackbarHost = snackbarHost,
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

                // Above everything, details included, unless match popups are turned off.
                val matchPopups = LocalAppSettings.current.matchPopups
                LaunchedEffect(state.matchPopup, matchPopups) { if (!matchPopups && state.matchPopup != null) viewModel.dismissMatch() }
                state.matchPopup?.takeIf { matchPopups }?.let { match ->
                    MatchPopup(
                        match = match,
                        myName = account?.name,
                        myAvatar = account?.avatar ?: Avatar.NONE,
                        onOpenProfile = { user ->
                            viewModel.dismissMatch()
                            viewModel.closeDetails()
                            navController.navigate(profileRoute(user.id))
                        },
                        onOpenGroup = { group ->
                            viewModel.dismissMatch()
                            viewModel.closeDetails()
                            navController.navigate(groupRoute(group.id))
                        },
                        onSeeMatches = {
                            viewModel.dismissMatch()
                            viewModel.closeDetails()
                            navController.navigateToSection(AppSection.MATCHES)
                        },
                        onKeepSwiping = viewModel::dismissMatch,
                    )
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
    friendsFactory: ViewModelProvider.Factory,
    matchesFactory: ViewModelProvider.Factory,
    groupsFactory: ViewModelProvider.Factory,
    chatFactory: ViewModelProvider.Factory,
    settingsFactory: ViewModelProvider.Factory,
    account: Account?,
    snackbarHost: SnackbarHostState,
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
        composable(AppSection.MATCHES.route) {
            val matches: MatchesViewModel = viewModel(factory = matchesFactory)
            LaunchedEffect(Unit) { matches.load() }
            MatchesScreen(
                state = matches.uiStateFlow.collectAsStateWithLifecycle().value,
                onRetry = matches::load,
                onMovieClick = { movie -> viewModel.selectMovie(MovieCategory.MATCH, movie) },
                onPersonClick = { person -> navController.navigate(profileRoute(person.id)) },
                onMenuClick = openMenu,
            )
        }
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
        composable(AppSection.FRIENDS.route) {
            val friends: FriendsViewModel = viewModel(factory = friendsFactory)
            LaunchedEffect(Unit) { friends.load() }
            FriendsScreen(
                state = friends.uiStateFlow.collectAsStateWithLifecycle().value,
                onRetry = friends::load,
                onAccept = friends::accept,
                onDecline = friends::decline,
                onCancel = friends::cancel,
                onRemove = friends::askRemove,
                onConfirmRemove = friends::confirmRemove,
                onCancelRemove = friends::cancelRemove,
                onPersonClick = { person -> navController.navigate(profileRoute(person.id)) },
                onMenuClick = openMenu,
            )
        }
        composable(AppSection.GROUPS.route) {
            val groups: GroupsViewModel = viewModel(factory = groupsFactory)
            LaunchedEffect(Unit) { groups.load() }
            GroupsScreen(
                state = groups.uiStateFlow.collectAsStateWithLifecycle().value,
                onRetry = groups::load,
                onGroupClick = { group -> navController.navigate(groupRoute(group.id)) },
                onNewGroup = { navController.navigate(NEW_GROUP_ROUTE) },
                onMenuClick = openMenu,
            )
        }
        composable(NEW_GROUP_ROUTE) {
            val create: CreateGroupViewModel = viewModel(factory = groupsFactory)
            val createState = create.uiStateFlow.collectAsStateWithLifecycle().value
            ShowMessage(createState.message, snackbarHost, create::messageShown)
            LaunchedEffect(createState.createdGroupId) {
                // Open the new group in place of this screen, so back returns to the list.
                createState.createdGroupId?.let { id ->
                    navController.navigate(groupRoute(id)) { popUpTo(NEW_GROUP_ROUTE) { inclusive = true } }
                }
            }
            CreateGroupScreen(
                state = createState,
                onBack = { navController.popBackStack() },
                onNameChange = create::onNameChange,
                onToggle = create::toggle,
                onRetryFriends = create::loadFriends,
                onCreate = create::create,
            )
        }
        composable(GROUP_ROUTE) { entry ->
            val groupId = entry.arguments?.getString(GROUP_ID).orEmpty()
            val group: GroupViewModel = viewModel(factory = groupsFactory)
            val groupState = group.uiStateFlow.collectAsStateWithLifecycle().value
            LaunchedEffect(groupId) { group.load(groupId) }
            ShowMessage(groupState.message, snackbarHost, group::messageShown)
            LaunchedEffect(groupState.left) { if (groupState.left) navController.popBackStack() }
            GroupScreen(
                state = groupState,
                myId = accountId,
                actions =
                    GroupActions(
                        retry = group::retry,
                        startAdding = group::startAdding,
                        stopAdding = group::stopAdding,
                        add = group::add,
                        askRemove = group::askRemove,
                        confirmRemove = group::confirmRemove,
                        cancelRemove = group::cancelRemove,
                        askLeave = group::askLeave,
                        confirmLeave = { accountId?.let(group::confirmLeave) },
                        confirmDisband = { accountId?.let(group::confirmDisband) },
                        cancelLeave = group::cancelLeave,
                        startRename = group::startRename,
                        onNameDraftChange = group::onNameDraftChange,
                        saveRename = group::saveRename,
                        cancelRename = group::cancelRename,
                    ),
                onBack = { navController.popBackStack() },
                onMovieClick = { movie -> viewModel.selectMovie(MovieCategory.MATCH, movie) },
                onPersonClick = { person -> navController.navigate(profileRoute(person.id)) },
                onOpenChat = { group -> navController.navigateOrBack(CHAT_ROUTE, CHAT_ID, group.conversationId, chatRoute(group.conversationId)) },
            )
        }
        composable(AppSection.CHATS.route) {
            val chats: ChatsViewModel = viewModel(factory = chatFactory)
            LaunchedEffect(Unit) { chats.load() }
            ChatsScreen(
                state = chats.uiStateFlow.collectAsStateWithLifecycle().value,
                myId = accountId,
                onRetry = chats::load,
                onChatClick = { conversation -> navController.navigate(chatRoute(conversation.id)) },
                onMenuClick = openMenu,
            )
        }
        composable(CHAT_ROUTE) { entry ->
            val conversationId = entry.arguments?.getString(CHAT_ID).orEmpty()
            ChatRoute(chatFactory, accountId, snackbarHost, navController) { chat, me -> chat.open(conversationId, me) }
        }
        composable(DIRECT_CHAT_ROUTE) { entry ->
            val userId = entry.arguments?.getString(CHAT_USER_ID).orEmpty()
            ChatRoute(chatFactory, accountId, snackbarHost, navController) { chat, me -> chat.openDirect(userId, me) }
        }
        composable(SETTINGS_ROUTE) {
            val settings: SettingsViewModel = viewModel(factory = settingsFactory)
            val settingsState = settings.uiStateFlow.collectAsStateWithLifecycle().value
            ShowMessage(settingsState.edit.message, snackbarHost, settings::messageShown)
            SettingsScreen(
                state = settingsState,
                viewModel = settings,
                onBack = { navController.popBackStack() },
                onLogout = onLogout,
            )
        }
        composable(AppSection.USER.route) {
            if (accountId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                // Keyed on the account, so changes made in settings show when you come back.
                ProfileRoute(
                    accountId,
                    profileFactory,
                    viewModel,
                    onMenuClick = openMenu,
                    onBack = null,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onMessage = {},
                    refreshKey = account,
                )
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
                onOpenSettings = {},
                onMessage = { id -> navController.navigateOrBack(DIRECT_CHAT_ROUTE, CHAT_USER_ID, id, directChatRoute(id)) },
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

/** Someone's profile, opened from search or a match: `profile/<userId>`. Each opened profile has its own ViewModel. */
private const val PROFILE_USER_ID = "userId"
private const val PROFILE_ROUTE = "profile/{$PROFILE_USER_ID}"

private fun profileRoute(userId: String) = "profile/$userId"

/** A group's own screen, `group/<groupId>`, and the new-group screen. */
private const val GROUP_ID = "groupId"
private const val GROUP_ROUTE = "group/{$GROUP_ID}"
private const val NEW_GROUP_ROUTE = "groups/new"

private fun groupRoute(groupId: String) = "group/$groupId"

/** Your settings, opened from your own profile. */
private const val SETTINGS_ROUTE = "settings"

/** A chat by id, `chat/<conversationId>`, or the one-to-one chat with a friend, `chat/with/<userId>`. */
private const val CHAT_ID = "conversationId"
private const val CHAT_ROUTE = "chat/{$CHAT_ID}"
private const val CHAT_USER_ID = "userId"
private const val DIRECT_CHAT_ROUTE = "chat/with/{$CHAT_USER_ID}"

private fun chatRoute(conversationId: String) = "chat/$conversationId"

private fun directChatRoute(userId: String) = "chat/with/$userId"

/** One chat screen; [open] says which chat, once the signed-in account is known. */
@Composable
private fun ChatRoute(
    chatFactory: ViewModelProvider.Factory,
    accountId: String?,
    snackbarHost: SnackbarHostState,
    navController: NavHostController,
    open: (ChatViewModel, myId: String) -> Unit,
) {
    val chat: ChatViewModel = viewModel(factory = chatFactory)
    LaunchedEffect(accountId) { accountId?.let { open(chat, it) } }
    val chatState = chat.uiStateFlow.collectAsStateWithLifecycle().value
    ShowMessage(chatState.message, snackbarHost, chat::messageShown)
    ChatScreen(
        state = chatState,
        isConnected = chat.isConnected.collectAsStateWithLifecycle().value,
        isMine = chat::isMine,
        onBack = { navController.popBackStack() },
        onTitleClick = { conversation ->
            // The group's screen, or the other person's profile.
            val groupId = conversation.groupId
            val otherId = conversation.members.firstOrNull()?.id
            when {
                groupId != null -> navController.navigateOrBack(GROUP_ROUTE, GROUP_ID, groupId, groupRoute(groupId))
                otherId != null -> navController.navigateOrBack(PROFILE_ROUTE, PROFILE_USER_ID, otherId, profileRoute(otherId))
            }
        },
        onRetry = chat::retry,
        onLoadOlder = chat::loadOlder,
        onDraftChange = chat::onDraftChange,
        onSend = chat::send,
        onRetrySend = chat::retrySend,
    )
}

/**
 * Opens [route], unless that very screen is the one right underneath: then it goes back to it.
 * Without this, group → chat → group info → chat … would stack a new copy each time, and back
 * would walk through all of them.
 */
private fun NavHostController.navigateOrBack(pattern: String, argName: String, argValue: String, route: String) {
    val previous = previousBackStackEntry
    if (previous?.destination?.route == pattern && previous.arguments?.getString(argName) == argValue) popBackStack()
    else navigate(route)
}

/** Shows [message] in the shared snackbar once, then tells the screen it was shown. */
@Composable
private fun ShowMessage(message: String?, snackbarHost: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            onShown()
        }
    }
}

@Composable
private fun ProfileRoute(
    userId: String,
    profileFactory: ViewModelProvider.Factory,
    homeViewModel: HomeViewModel,
    onMenuClick: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onMessage: (userId: String) -> Unit,
    /** Reloads the profile when this changes (your own, after editing it in settings). */
    refreshKey: Any? = null,
) {
    val profile: ProfileViewModel = viewModel(factory = profileFactory)
    LaunchedEffect(userId, refreshKey) { profile.load(userId, refresh = refreshKey != null) }
    ProfileScreen(
        state = profile.uiStateFlow.collectAsStateWithLifecycle().value,
        onRetry = profile::retry,
        onMovieClick = { movie -> homeViewModel.selectMovie(MovieCategory.PROFILE, movie) },
        onMenuClick = onMenuClick,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        friendActions =
            ProfileFriendActions(
                add = profile::addFriend,
                accept = profile::acceptFriend,
                decline = profile::declineFriend,
                cancelRequest = profile::cancelFriendRequest,
                askUnfriend = profile::askUnfriend,
                confirmUnfriend = profile::confirmUnfriend,
                cancelUnfriend = profile::cancelUnfriend,
                message = { onMessage(userId) },
            ),
    )
}
