package com.example.movielist

import android.app.Application

class MovieListApp : Application() {
    val appContainer by lazy { AppContainer(this) }
}
