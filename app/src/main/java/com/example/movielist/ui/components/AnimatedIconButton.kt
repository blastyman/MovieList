package com.example.movielist.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

@Composable
fun AnimatedIconButton(
    icon: ImageVector,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var clicked by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by
        animateFloatAsState(
            targetValue =
                when {
                    pressed -> 1.18f
                    clicked -> 1.10f
                    else -> 1f
                },
            animationSpec = tween(120),
            label = "buttonScale",
        )

    LaunchedEffect(clicked) {
        if (clicked) {
            delay(120)
            clicked = false
        }
    }

    Button(
        onClick = {
            clicked = true
            onClick()
        },
        modifier = Modifier.scale(scale),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White)
    }
}
