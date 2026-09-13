package com.example.movielist.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.movielist.domain.model.AppUser
import com.example.movielist.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

class DataStoreUserPreferencesRepository(private val context: Context) : UserPreferencesRepository {
    override suspend fun loadUsers(defaultUsers: List<AppUser>): List<AppUser> {
        val preferences = context.userPreferencesDataStore.data.first()
        return defaultUsers.mapIndexed { index, defaultUser ->
            AppUser(
                name = preferences[nameKey(index)] ?: defaultUser.name,
                likedMovieIds = preferences[likedKey(index)].toMovieIds(),
                dislikedMovieIds = preferences[dislikedKey(index)].toMovieIds(),
            )
        }
    }

    override suspend fun saveUsers(users: List<AppUser>) {
        context.userPreferencesDataStore.edit { preferences ->
            users.forEachIndexed { index, user ->
                preferences[nameKey(index)] = user.name
                preferences[likedKey(index)] = user.likedMovieIds.toStorageValue()
                preferences[dislikedKey(index)] = user.dislikedMovieIds.toStorageValue()
            }
        }
    }

    private fun String?.toMovieIds(): Set<Int> =
        this.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()

    private fun Set<Int>.toStorageValue(): String = sorted().joinToString(",")

    private companion object {
        fun nameKey(index: Int) = stringPreferencesKey("user_${index}_name")

        fun likedKey(index: Int) = stringPreferencesKey("user_${index}_liked")

        fun dislikedKey(index: Int) = stringPreferencesKey("user_${index}_disliked")
    }
}
