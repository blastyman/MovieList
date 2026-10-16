package com.example.movielist.ui.groups

import com.example.movielist.ui.utils.toUserMessage
import retrofit2.HttpException

/** Members per group, owner included; the server enforces it too. */
const val MAX_GROUP_MEMBERS = 10
const val MAX_GROUP_NAME = 50

fun fullMessage() = "Groups can have up to $MAX_GROUP_MEMBERS people, you included."

/** Like [toUserMessage], but says what a 409 or 403 means for groups. */
fun Throwable.toGroupMessage(): String =
    when {
        this is HttpException && code() == 409 -> fullMessage()
        this is HttpException && code() == 403 -> "You can only add your own friends, and only the owner can remove people."
        else -> toUserMessage()
    }
