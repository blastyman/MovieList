package com.example.movielist.ui.settings

import androidx.compose.runtime.compositionLocalOf
import com.example.movielist.domain.repository.AppSettings

/**
 * The phone's app settings, for the few places deep in the UI that follow them (swipe card,
 * trailer player, match popup) without passing them through every screen. MainActivity
 * provides the live value.
 */
val LocalAppSettings = compositionLocalOf { AppSettings() }
