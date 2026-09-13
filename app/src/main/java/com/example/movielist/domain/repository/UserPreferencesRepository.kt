package com.example.movielist.domain.repository

import com.example.movielist.domain.model.AppUser

interface UserPreferencesRepository {
    suspend fun loadUsers(defaultUsers: List<AppUser>): List<AppUser>

    suspend fun saveUsers(users: List<AppUser>)
}

class InMemoryUserPreferencesRepository : UserPreferencesRepository {
    private var users: List<AppUser>? = null

    override suspend fun loadUsers(defaultUsers: List<AppUser>): List<AppUser> =
        users ?: defaultUsers

    override suspend fun saveUsers(users: List<AppUser>) {
        this.users = users
    }
}
