package com.example.movielist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movielist.domain.model.SessionState
import com.example.movielist.ui.auth.AuthScreen
import com.example.movielist.ui.home.HomeScreen
import com.example.movielist.ui.theme.MovieListTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer
        get() = (application as MovieListApp).appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authRepository = appContainer.authRepository

        setContent {
            MovieListTheme {
                val session = authRepository.session.collectAsStateWithLifecycle().value
                val account = authRepository.account.collectAsStateWithLifecycle().value
                val scope = rememberCoroutineScope()

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
                        // A saved token has no account loaded yet; if the token was rejected,
                        // the repository ends the session and this screen switches to login.
                        LaunchedEffect(Unit) {
                            if (account == null) authRepository.refreshAccount()
                        }
                        HomeScreen(
                            factory = appContainer.homeViewModelFactory(),
                            accountUsername = account?.username,
                            onLogout = { scope.launch { authRepository.logout() } },
                        )
                    }
                }
            }
        }
    }
}
