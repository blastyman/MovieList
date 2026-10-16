package com.example.movielist.data.model

import com.example.movielist.BuildConfig
import com.example.movielist.domain.model.Avatar

/** The server sends photos as paths on itself (`/avatars/…`); the app needs full addresses. */
fun avatarOf(url: String?, emoji: String?, color: String?) =
    Avatar(
        photoUrl = url?.let { if (it.startsWith("http")) it else BuildConfig.API_BASE_URL.trimEnd('/') + it },
        emoji = emoji,
        color = color,
    )
