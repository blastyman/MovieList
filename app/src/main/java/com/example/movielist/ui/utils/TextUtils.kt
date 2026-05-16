package com.example.movielist.ui.utils

fun shortOverview(text: String): String {

    if (text.isBlank()) {
        return "No description available."
    }

    val cleaned = text.trim()
    val firstSentence = cleaned.substringBefore(".")

    return if (firstSentence.length in 21..140) {
        "$firstSentence."
    } else if (cleaned.length > 140) {
        cleaned.take(140).trim() + "..."
    } else {
        cleaned
    }
}