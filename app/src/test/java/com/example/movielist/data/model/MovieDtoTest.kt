package com.example.movielist.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieDtoTest {
    @Test
    fun mapsBackendFieldsToDomainMovie() {
        val json =
            """{"id":1,"title":"Title","overview":"Overview","posterPath":"/poster.jpg","genreIds":[28,35],"backdropPath":null,"voteAverage":7.5}"""
        val movie = Gson().fromJson(json, MovieDto::class.java).toMovie()
        assertEquals("/poster.jpg", movie.posterPath)
        assertEquals(listOf(28, 35), movie.genreIds)
    }

    @Test
    fun absentOptionalFieldsHaveSafeDomainValues() {
        val movie = Gson().fromJson("""{"id":1}""", MovieDto::class.java).toMovie()
        assertEquals("", movie.title)
        assertEquals("", movie.overview)
        assertNull(movie.posterPath)
        assertTrue(movie.genreIds.isEmpty())
    }
}
