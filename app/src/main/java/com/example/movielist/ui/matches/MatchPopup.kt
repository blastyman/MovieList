package com.example.movielist.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movielist.domain.model.Match
import com.example.movielist.ui.theme.MovieRed
import kotlinx.coroutines.delay

@Composable
fun MatchPopup(match: Match, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(match) {
        delay(MATCH_POPUP_DURATION_MILLIS)
        dismiss()
    }

    Box(
        modifier =
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.70f)).clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MovieRed),
        ) {
            Column(
                modifier = Modifier.padding(34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(com.example.movielist.R.string.match),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text =
                        stringResource(
                            com.example.movielist.R.string.match_description,
                            match.userName,
                        ),
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val MATCH_POPUP_DURATION_MILLIS = 5_000L
