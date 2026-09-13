package com.example.movielist.domain.service

import com.example.movielist.domain.model.AppUser
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieCatalogTest {
    @Test
    fun genresDoNotRepeatMoviesAcrossSections() {
        val movies =
            listOf(
                Movie(1, "Comedy action", "", null, listOf(35, 28)),
                Movie(2, "Action", "", null, listOf(28)),
                Movie(3, "Romance", "", null, listOf(10749)),
            )
        val categories = MovieCatalog.categorize(movies)
        assertEquals(listOf(movies[0]), categories[MovieCategory.COMEDY])
        assertEquals(listOf(movies[1]), categories[MovieCategory.ACTION])
        assertEquals(listOf(movies[2]), categories[MovieCategory.ROMANCE])
        assertEquals(movies, categories[MovieCategory.POPULAR])
        assertEquals(movies.toSet(), categories[MovieCategory.RECOMMENDED]?.toSet())
        assertTrue(categories[MovieCategory.SCI_FI].orEmpty().isEmpty())
    }

    @Test
    fun matchingUsesMovieIdsEvenWhenTitlesAreIdentical() {
        val movies =
            listOf(
                Movie(1, "Same title", "", null, emptyList()),
                Movie(2, "Same title", "", null, emptyList()),
            )
        val users =
            listOf(
                AppUser("First", likedMovieIds = setOf(1, 2)),
                AppUser("Second", likedMovieIds = setOf(1, 2)),
            )
        val matches = MovieCatalog.findMatches(movies, users, 0)
        assertEquals(listOf(1, 2), matches.map { it.movieId })
        assertTrue(matches.all { it.userName == "Second" && it.posterPath == null })
    }
}
