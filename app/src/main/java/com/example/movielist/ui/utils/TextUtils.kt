package com.example.movielist.ui.utils

fun shortOverview(text: String): String {

    if (text.isBlank()) {
        return "No description available."
    }

    val cleaned = text.trim()
    val firstSentence = cleaned.substringBefore('.')

    return if (firstSentence.length in MIN_SENTENCE_LENGTH..MAX_OVERVIEW_LENGTH) {
        "$firstSentence."
    } else if (cleaned.length > MAX_OVERVIEW_LENGTH) {
        cleaned.take(MAX_OVERVIEW_LENGTH).trimEnd() + "..."
    } else {
        cleaned
    }
}

private const val MIN_SENTENCE_LENGTH = 21
private const val MAX_OVERVIEW_LENGTH = 140
