package com.example.movielist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.movielist.domain.model.Avatar
import com.example.movielist.ui.theme.MovieRed

/**
 * Someone in a circle: their photo, else their emoji on their colour, else the first letter of
 * [name]. The letter also shows while a photo loads, or if it can't.
 */
@Composable
fun UserAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp, avatar: Avatar = Avatar.NONE) {
    val background = avatar.color?.let(::parseColor) ?: MovieRed
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar.emoji != null && avatar.photoUrl == null) {
            Text(text = avatar.emoji, fontSize = (size.value * 0.5f).sp)
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
        if (avatar.photoUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(avatar.photoUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** `#RRGGBB` as a colour, or null if it isn't one. */
fun parseColor(hex: String): Color? =
    hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
