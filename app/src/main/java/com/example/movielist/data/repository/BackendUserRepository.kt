package com.example.movielist.data.repository

import com.example.movielist.data.remote.UserApi
import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.UserRepository

class BackendUserRepository(private val api: UserApi) : UserRepository {
    override suspend fun search(query: String, page: Int): SearchPage<UserSummary> =
        api.search(query, page).toSearchPage()

    override suspend fun profile(userId: String): UserProfile = api.profile(userId).toUserProfile()
}
