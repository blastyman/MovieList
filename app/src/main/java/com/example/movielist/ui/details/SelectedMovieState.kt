package com.example.movielist.ui.details

import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory

data class SelectedMovieState(val category: MovieCategory, val movie: Movie)
