package com.example.movielist.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** How the app behaves on this phone. Not an account setting: it stays on the device. */
data class AppSettings(
    /** Swipe right to like, like Tinder; off keeps the original swipe left to like. */
    val swipeRightToLike: Boolean = false,
    /** Trailers start playing as soon as they open. */
    val autoplayTrailers: Boolean = true,
    /** Show the "It's a Match!" popup after liking a movie someone else likes. */
    val matchPopups: Boolean = true,
)

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun update(change: (AppSettings) -> AppSettings)
}

/** Keeps settings in memory. Used by tests and previews. */
class InMemoryAppSettings(initial: AppSettings = AppSettings()) : AppSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state

    override suspend fun update(change: (AppSettings) -> AppSettings) = state.update(change)
}
