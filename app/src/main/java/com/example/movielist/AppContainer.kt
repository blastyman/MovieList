package com.example.movielist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.movielist.data.preferences.DataStoreTokenStore
import com.example.movielist.data.remote.AuthApi
import com.example.movielist.data.remote.BackendClient
import com.example.movielist.data.remote.CommentApi
import com.example.movielist.data.remote.FriendApi
import com.example.movielist.data.remote.MovieApi
import com.example.movielist.data.remote.MovieSearchApi
import com.example.movielist.data.remote.RatingApi
import com.example.movielist.data.remote.ReactionApi
import com.example.movielist.data.remote.TrailerApi
import com.example.movielist.data.remote.UserApi
import com.example.movielist.data.repository.BackendAuthRepository
import com.example.movielist.data.repository.BackendCommentRepository
import com.example.movielist.data.repository.BackendFriendRepository
import com.example.movielist.data.repository.BackendMovieRepository
import com.example.movielist.data.repository.BackendMovieSearchRepository
import com.example.movielist.data.repository.BackendRatingRepository
import com.example.movielist.data.repository.BackendReactionRepository
import com.example.movielist.data.repository.BackendTrailerRepository
import com.example.movielist.data.repository.BackendUserRepository
import com.example.movielist.domain.repository.AuthRepository
import com.example.movielist.ui.auth.AuthViewModel
import com.example.movielist.ui.comments.CommentsViewModel
import com.example.movielist.ui.friends.FriendsViewModel
import com.example.movielist.ui.home.HomeViewModel
import com.example.movielist.ui.profile.ProfileViewModel
import com.example.movielist.ui.search.SearchViewModel
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
    private val trailerRepository by lazy { BackendTrailerRepository(backend.create(TrailerApi::class.java)) }
    private val commentRepository by lazy { BackendCommentRepository(backend.create(CommentApi::class.java)) }
    private val userRepository by lazy { BackendUserRepository(backend.create(UserApi::class.java)) }
    private val friendRepository by lazy { BackendFriendRepository(backend.create(FriendApi::class.java)) }
    private val movieSearchRepository by lazy {
        BackendMovieSearchRepository(backend.create(MovieSearchApi::class.java))
    }

    fun homeViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory { HomeViewModel(movieRepository, reactionRepository, ratingRepository, trailerRepository) }

    fun commentsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory { CommentsViewModel(commentRepository) }

    fun searchViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory { SearchViewModel(movieSearchRepository, userRepository) }

    fun profileViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory { ProfileViewModel(userRepository, authRepository, friendRepository) }

    fun friendsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory { FriendsViewModel(friendRepository) }

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
