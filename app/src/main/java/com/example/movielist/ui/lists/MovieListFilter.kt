package com.example.movielist.ui.lists

import com.example.movielist.domain.model.MovieCategory

enum class MovieListFilter(val category: MovieCategory) {
    LIKED(MovieCategory.LIKED),
    DISLIKED(MovieCategory.DISLIKED),
}
