package com.example.movielist.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Modifier.pressAnimatedClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by
        animateFloatAsState(
            targetValue = if (pressed) 1.06f else 1f,
            animationSpec = tween(100),
            label = "scale",
        )

    val alpha by
        animateFloatAsState(
            targetValue = if (pressed) 0.75f else 1f,
            animationSpec = tween(100),
            label = "alpha",
        )

    return this.scale(scale)
        .graphicsLayer { this.alpha = alpha }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
