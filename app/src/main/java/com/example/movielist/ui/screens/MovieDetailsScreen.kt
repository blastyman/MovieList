package com.example.movielist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.movielist.data.model.Movie
import com.example.movielist.ui.components.AnimatedIconButton
import com.example.movielist.ui.utils.shortOverview
import kotlin.math.abs
import kotlin.math.roundToInt

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
    var dragOffset by remember(movie.id) { mutableFloatStateOf(0f) }

    val shortText = shortOverview(movie.overview)
    val needsExpansion = movie.overview.length > shortText.length
    val swipeThreshold = 220f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .graphicsLayer {
                    rotationZ = dragOffset / 35f
                    alpha = 1f - (abs(dragOffset) / 900f)
                }
                .pointerInput(movie.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragOffset > swipeThreshold -> onLike()
                                dragOffset < -swipeThreshold -> onDislike()
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset += dragAmount
                        }
                    )
                }
        ) {
            AsyncImage(
                model = movie.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                contentDescription = movie.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(Alignment.BottomCenter)
                    .clip(
                        RoundedCornerShape(
                            bottomStart = 28.dp,
                            bottomEnd = 28.dp
                        )
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.35f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-10).dp),
                horizontalArrangement = Arrangement.spacedBy(90.dp)
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = movie.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (expanded) movie.overview.ifBlank { "No description available." } else shortText,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray,
            maxLines = if (expanded) Int.MAX_VALUE else 5
        )

        if (!expanded && needsExpansion) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color(0xFFE50914)
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 14.dp,
                        vertical = 2.dp
                    )
                ) {
                    Text(
                        text = "read more",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}