package com.example.movielist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.movielist.data.preferences.DataStoreUserPreferencesRepository
import com.example.movielist.data.remote.TmdbClient
import com.example.movielist.data.repository.TmdbMovieRepository
import com.example.movielist.domain.repository.UserPreferencesRepository
import com.example.movielist.ui.home.HomeViewModel

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val movieRepository = TmdbMovieRepository(TmdbClient.createApi(BuildConfig.TMDB_TOKEN))
    private val userPreferencesRepository: UserPreferencesRepository =
        DataStoreUserPreferencesRepository(applicationContext)

    fun homeViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(movieRepository, userPreferencesRepository) as T
            }
        }
}
