package com.example.movielist.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Kept out of Android backups (res/xml/backup_rules.xml): a token must not follow the user to another device.
private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Persists the backend JWT. [current] is an in-memory copy so the OkHttp interceptor can
 * read the token synchronously on every request.
 */
class DataStoreTokenStore(private val context: Context) {
    @Volatile
    var current: String? = null
        private set

    suspend fun load(): String? =
        context.authDataStore.data.first()[TOKEN_KEY].also { current = it }

    suspend fun save(token: String) {
        context.authDataStore.edit { it[TOKEN_KEY] = token }
        current = token
    }

    suspend fun clear() {
        current = null
        context.authDataStore.edit { it.remove(TOKEN_KEY) }
    }

    private companion object {
        val TOKEN_KEY = stringPreferencesKey("jwt")
    }
}
