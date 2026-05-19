package com.example.movielist.domain.state

import com.example.movielist.data.model.Movie
import com.example.movielist.domain.model.MovieCategory

data class SelectedMovieState(
    val category: MovieCategory,
    val movie: Movie
)