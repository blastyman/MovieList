package com.example.movielist.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.movielist.domain.repository.AppSettings
import com.example.movielist.domain.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** App settings saved on the phone; screens read [settings] and follow every change. */
class DataStoreAppSettings(private val context: Context, scope: CoroutineScope) : AppSettingsRepository {
    override val settings: StateFlow<AppSettings> =
        context.settingsDataStore.data
            .map { it.toSettings() }
            .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = change(prefs.toSettings())
            prefs[SWIPE_RIGHT_TO_LIKE] = updated.swipeRightToLike
            prefs[AUTOPLAY_TRAILERS] = updated.autoplayTrailers
            prefs[MATCH_POPUPS] = updated.matchPopups
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            swipeRightToLike = this[SWIPE_RIGHT_TO_LIKE] ?: defaults.swipeRightToLike,
            autoplayTrailers = this[AUTOPLAY_TRAILERS] ?: defaults.autoplayTrailers,
            matchPopups = this[MATCH_POPUPS] ?: defaults.matchPopups,
        )
    }

    private companion object {
        val SWIPE_RIGHT_TO_LIKE = booleanPreferencesKey("swipe_right_to_like")
        val AUTOPLAY_TRAILERS = booleanPreferencesKey("autoplay_trailers")
        val MATCH_POPUPS = booleanPreferencesKey("match_popups")
    }
}
