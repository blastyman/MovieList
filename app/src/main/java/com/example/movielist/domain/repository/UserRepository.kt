package com.example.movielist.domain.repository

import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.model.UserSummary

interface UserRepository {
    /** People whose username or display name contains [query] (at least 2 characters). */
    suspend fun search(query: String, page: Int = 1): SearchPage<UserSummary>

    suspend fun profile(userId: String): UserProfile
}
