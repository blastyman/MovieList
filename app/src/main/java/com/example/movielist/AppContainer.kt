package com.example.movielist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.movielist.data.preferences.DataStoreTokenStore
import com.example.movielist.data.remote.AuthApi
import com.example.movielist.data.remote.BackendClient
import com.example.movielist.data.remote.MovieApi
import com.example.movielist.data.remote.RatingApi
import com.example.movielist.data.remote.ReactionApi
import com.example.movielist.data.repository.BackendAuthRepository
import com.example.movielist.data.repository.BackendMovieRepository
import com.example.movielist.data.repository.BackendRatingRepository
import com.example.movielist.data.repository.BackendReactionRepository
import com.example.movielist.domain.repository.AuthRepository
import com.example.movielist.ui.auth.AuthViewModel
import com.example.movielist.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** One instance per process, owned by [MovieListApp]. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    /** For work that must outlive any single screen, like ending a rejected session. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val tokenStore = DataStoreTokenStore(applicationContext)

    /** One HTTP client for every backend call, so all of them carry the login token. */
    private val backend by lazy {
        BackendClient.create(
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = { tokenStore.current },
            onUnauthorized = { backendAuthRepository.onTokenRejected() },
        )
    }

    private val backendAuthRepository: BackendAuthRepository by lazy {
        BackendAuthRepository(backend.create(AuthApi::class.java), tokenStore, appScope)
    }
    val authRepository: AuthRepository
        get() = backendAuthRepository

    private val movieRepository by lazy { BackendMovieRepository(backend.create(MovieApi::class.java)) }
    private val reactionRepository by lazy {
        BackendReactionRepository(backend.create(ReactionApi::class.java))
    }
    private val ratingRepository by lazy { BackendRatingRepository(backend.create(RatingApi::class.java)) }

    fun homeViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory { HomeViewModel(movieRepository, reactionRepository, ratingRepository) }

    fun authViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory { AuthViewModel(authRepository) }

    private inline fun <reified VM : ViewModel> viewModelFactory(
        crossinline create: () -> VM
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(VM::class.java))
                @Suppress("UNCHECKED_CAST")
                return create() as T
            }
        }
}
