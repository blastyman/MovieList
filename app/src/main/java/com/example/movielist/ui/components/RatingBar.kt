package com.example.movielist.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.ui.theme.MovieRed

/**
 * Five stars with halves for a 1–10 [score] (score 7 = 3½ stars). Tapping the left half of a
 * star gives the half step. Tapping the current score again clears it ([onScoreChange] null).
 */
@Composable
fun RatingBar(
    score: Int?,
    onScoreChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 36.dp,
) {
    val currentScore by rememberUpdatedState(score)
    val latestOnScoreChange by rememberUpdatedState(onScoreChange)
    fun choose(newScore: Int) = latestOnScoreChange(if (newScore == currentScore) null else newScore)

    Row(modifier = modifier) {
        for (star in 1..STARS) {
            val fullScore = star * 2
            val label = pluralStringResource(R.plurals.rating_star, star, star)
            Box(
                modifier =
                    Modifier.size(starSize)
                        .pointerInput(star) {
                            detectTapGestures { offset ->
                                choose(if (offset.x < size.width / 2) fullScore - 1 else fullScore)
                            }
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = label
                            onClick {
                                choose(fullScore)
                                true
                            }
                        }
            ) {
                val value = score ?: 0
                Icon(
                    imageVector =
                        when {
                            value >= fullScore -> Icons.Filled.Star
                            value == fullScore - 1 -> Icons.AutoMirrored.Filled.StarHalf
                            else -> Icons.Filled.StarBorder
                        },
                    contentDescription = null,
                    tint = if (value >= fullScore - 1) MovieRed else Color.Gray,
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}

private const val STARS = 5
