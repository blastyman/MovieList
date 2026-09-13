package com.example.movielist.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.movielist.domain.model.Movie
import com.example.movielist.ui.theme.MovieButton
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface
import com.example.movielist.ui.utils.shortOverview

@Composable
fun RecommendedMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
) {
    val latestOnLike by rememberUpdatedState(onLike)
    val latestOnDislike by rememberUpdatedState(onDislike)
    var offsetX by remember(movie.id) { mutableFloatStateOf(0f) }

    val animatedOffsetX by
        animateFloatAsState(targetValue = offsetX, label = "recommendedCardOffset")

    val rotation by
        animateFloatAsState(targetValue = offsetX / 40f, label = "recommendedCardRotation")

    Card(
        modifier =
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffsetX
                    rotationZ = rotation
                }
                .pointerInput(movie.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount },
                        onDragEnd = {
                            when {
                                offsetX > 300f -> latestOnDislike()
                                offsetX < -300f -> latestOnLike()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                    )
                }
                .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MovieSurface),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)) {
            MovieImage(
                posterPath = movie.posterPath,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                width = 780,
            )

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(190.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MovieSurface.copy(alpha = 0.95f))
                            )
                        )
            )

            Column(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = 72.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = shortOverview(movie.overview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-18).dp),
                horizontalArrangement = Arrangement.spacedBy(90.dp),
            ) {
                AnimatedIconButton(
                    icon = Icons.Default.ThumbDown,
                    contentDescription = "Dislike",
                    color = MovieButton,
                    onClick = onDislike,
                )

                AnimatedIconButton(
                    icon = Icons.Default.ThumbUp,
                    contentDescription = "Like",
                    color = MovieRed,
                    onClick = onLike,
                )
            }
        }
    }
}
