package com.example.movielist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.movielist.ui.home.HomeScreen
import com.example.movielist.ui.theme.MovieListTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MovieListTheme { HomeScreen(appContainer.homeViewModelFactory()) } }
    }
}
