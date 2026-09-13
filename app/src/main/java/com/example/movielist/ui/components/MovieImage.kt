package com.example.movielist.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.movielist.ui.utils.posterUrl

@Composable
fun MovieImage(
    posterPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    width: Int = 500,
) {
    val placeholder = remember { ColorPainter(Color.DarkGray) }
    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(posterUrl(posterPath, width))
                .crossfade(true)
                .build(),
        contentDescription = contentDescription,
        modifier = modifier.background(Color.DarkGray),
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
    )
}
