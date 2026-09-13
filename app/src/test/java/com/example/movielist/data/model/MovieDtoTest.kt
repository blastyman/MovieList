package com.example.movielist.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieDtoTest {
    @Test
    fun mapsApiFieldNamesToCamelCaseProperties() {
        val json =
            """{"id":1,"title":"Title","overview":"Overview","poster_path":"/poster.jpg","genre_ids":[28,35]}"""
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
