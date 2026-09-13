package com.example.movielist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.movielist.domain.model.Movie

@Composable
fun MoviePosterItem(movie: Movie, onClick: () -> Unit) {
    Column(
        modifier = Modifier.pressAnimatedClickable(onClick).width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MovieImage(
            posterPath = movie.posterPath,
            contentDescription = movie.title,
            modifier = Modifier.width(140.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)),
            width = 342,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = movie.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
