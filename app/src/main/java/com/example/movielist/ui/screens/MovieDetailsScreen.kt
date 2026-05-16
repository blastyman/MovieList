package com.example.movielist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.movielist.data.model.Movie
import com.example.movielist.ui.components.AnimatedIconButton
import com.example.movielist.ui.utils.shortOverview

@Composable
fun MovieDetailsScreen(
    category: String,
    movie: Movie,
    isLiked: Boolean,
    isDisliked: Boolean,
    onBack: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    var expanded by remember(movie.id) { mutableStateOf(false) }
    val shortText = shortOverview(movie.overview)
    val needsDots = movie.overview.length > shortText.length

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = category,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                ),
                color = Color(0xFFE50914)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AsyncImage(
            model = movie.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
            contentDescription = movie.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = movie.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (expanded) {
            Text(
                text = movie.overview.ifBlank { "No description available." },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray
            )
        } else {
            Text(
                text = buildAnnotatedString {
                    append(if (needsDots) shortText.removeSuffix("...").trimEnd() else shortText)
                    if (needsDots) {
                        withStyle(style = SpanStyle(color = Color(0xFFE50914))) {
                            append("...")
                        }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray,
                modifier = Modifier.pointerInput(movie.id) {
                    detectTapGestures {
                        if (needsDots) expanded = true
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AnimatedIconButton(
                icon = Icons.Default.ThumbDown,
                color = if (isDisliked) Color(0xFFB00020) else Color(0xFF2C2C2C),
                onClick = onDislike
            )

            AnimatedIconButton(
                icon = Icons.Default.ThumbUp,
                color = if (isLiked) Color(0xFF00A86B) else Color(0xFFE50914),
                onClick = onLike
            )
        }
    }
}