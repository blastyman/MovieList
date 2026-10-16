package com.example.movielist.ui.utils

import java.io.IOException
import retrofit2.HttpException

/** A short sentence for the user about why a backend call failed. */
fun Throwable.toUserMessage(): String =
    when {
        // The session is also ended automatically, which brings back the login screen.
        this is HttpException && code() == 401 -> "Your session expired. Please log in again."
        this is HttpException && code() == 403 -> "You can't do that."
        this is HttpException && code() == 404 -> "It isn't there any more."
        this is HttpException && code() == 429 -> "Too many requests. Try again shortly."
        this is HttpException && code() == 502 -> "The movie database is unavailable right now."
        this is HttpException && code() >= 500 -> "The server is temporarily unavailable."
        this is IOException -> "Can't reach the server. Check your connection."
        message.isNullOrBlank() -> "Something went wrong."
        else -> message.orEmpty()
    }
