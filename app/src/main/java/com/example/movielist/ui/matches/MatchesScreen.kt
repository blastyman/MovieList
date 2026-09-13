package com.example.movielist.ui.matches

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movielist.domain.model.Match
import com.example.movielist.ui.components.MovieImage
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.theme.MovieSurface

@Composable
fun MatchesScreen(matches: List<Match>, onMenuClick: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenHeader("matches", onMenuClick)

        Spacer(modifier = Modifier.height(24.dp))

        if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(com.example.movielist.R.string.no_matches),
                    color = Color.White,
                    fontSize = 18.sp,
                )
            }
        } else {
            LazyColumn {
                items(matches, key = { "${it.userName}:${it.movieId}" }) { match ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MovieSurface),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MovieImage(
                                posterPath = match.posterPath,
                                contentDescription = match.movieTitle,
                                modifier = Modifier.size(100.dp).clip(RoundedCornerShape(18.dp)),
                                width = 342,
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = match.movieTitle,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text =
                                        stringResource(
                                            com.example.movielist.R.string.both_like_movie,
                                            match.userName,
                                        ),
                                    color = Color.LightGray,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
