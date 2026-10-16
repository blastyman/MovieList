package com.example.movielist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movielist.domain.model.SessionState
import com.example.movielist.ui.auth.AuthScreen
import com.example.movielist.ui.home.HomeScreen
import com.example.movielist.ui.settings.LocalAppSettings
import com.example.movielist.ui.theme.MovieListTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer
        get() = (application as MovieListApp).appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authRepository = appContainer.authRepository

        setContent {
            // App settings reach the few places deep in the UI that follow them.
            val appSettings = appContainer.appSettings.settings.collectAsStateWithLifecycle().value
            CompositionLocalProvider(LocalAppSettings provides appSettings) {
            MovieListTheme {
                val session = authRepository.session.collectAsStateWithLifecycle().value
                val account = authRepository.account.collectAsStateWithLifecycle().value
                val scope = rememberCoroutineScope()

                // Bumped on every logout, so the next login gets a fresh HomeViewModel instead
                // of the previous account's likes. Saveable, so rotation keeps the same one.
                var loginGeneration by rememberSaveable { mutableIntStateOf(0) }
                LaunchedEffect(session) { if (session == SessionState.LoggedOut) loginGeneration++ }

                when (session) {
                    SessionState.Loading ->
                        Box(
                            modifier =
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    SessionState.LoggedOut -> AuthScreen(appContainer.authViewModelFactory())
                    SessionState.LoggedIn -> {
                        // A saved token has no account loaded yet. Keep trying, backing off, if the
                        // server can't be reached: screens need it to know who "you" are. If the token
                        // was rejected, the repository ends the session and this screen switches to login.
                        LaunchedEffect(Unit) {
                            var wait = ACCOUNT_RETRY_START_MS
                            while (authRepository.account.value == null) {
                                if (authRepository.refreshAccount().isSuccess) break
                                delay(wait)
                                wait = (wait * 2).coerceAtMost(ACCOUNT_RETRY_MAX_MS)
                            }
                        }
                        // Chat's live connection: open while the app is on screen, closed in the background.
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val socket = appContainer.chatSocket
                            val observer = LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_START -> socket.connect()
                                    Lifecycle.Event.ON_STOP -> socket.disconnect()
                                    else -> Unit
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                                socket.disconnect() // logged out
                            }
                        }
                        HomeScreen(
                            factory = appContainer.homeViewModelFactory(),
                            commentsFactory = appContainer.commentsViewModelFactory(),
                            searchFactory = appContainer.searchViewModelFactory(),
                            profileFactory = appContainer.profileViewModelFactory(),
                            friendsFactory = appContainer.friendsViewModelFactory(),
                            matchesFactory = appContainer.matchesViewModelFactory(),
                            groupsFactory = appContainer.groupsViewModelFactory(),
                            chatFactory = appContainer.chatViewModelFactory(),
                            settingsFactory = appContainer.settingsViewModelFactory(),
                            viewModelKey = "home-$loginGeneration",
                            account = account,
                            onLogout = { scope.launch { authRepository.logout() } },
                        )
                    }
                }
            }
            }
        }
    }
}

/** Waits between attempts to load the signed-in account while the server is unreachable. */
private const val ACCOUNT_RETRY_START_MS = 2_000L
private const val ACCOUNT_RETRY_MAX_MS = 30_000L
