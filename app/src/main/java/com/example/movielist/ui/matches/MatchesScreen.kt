package com.example.movielist.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.model.MatchPerson
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.ui.components.MovieImage
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

/** Movies you like that other people like too, newest first, with those people to meet. */
@Composable
fun MatchesScreen(
    state: MatchesUiState,
    onRetry: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onPersonClick: (UserSummary) -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader("matches", onMenuClick)

        when {
            state.error != null && state.matches.isEmpty() ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = Color.LightGray, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MovieRed) }
                }
            !state.loaded ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.matches.isEmpty() ->
                Text(
                    text = stringResource(R.string.no_matches),
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                )
            else ->
                LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.matches, key = { it.movie.id }) { match -> MatchCard(match, onClick = { onMovieClick(match.movie) }, onPersonClick = onPersonClick) }
                }
        }
    }
}

@Composable
private fun MatchCard(match: Match, onClick: () -> Unit, onPersonClick: (UserSummary) -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MovieSurface),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MovieImage(
                posterPath = match.movie.posterPath,
                contentDescription = match.movie.title,
                modifier = Modifier.width(72.dp).height(108.dp).clip(RoundedCornerShape(12.dp)),
                width = 185,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.movie.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = peopleSentence(match), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
        // The people who like it, closest taste first; tap one to see their profile.
        LazyRow(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(match.people, key = { it.user.id }) { person -> PersonChip(person) { onPersonClick(person.user) } }
            val more = match.peopleCount - match.people.size
            if (more > 0) {
                item {
                    Text(
                        pluralStringResource(R.plurals.match_more_people, more, more),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonChip(person: MatchPerson, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClickLabel = stringResource(R.string.match_open_profile, person.user.name), onClick = onClick)
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    ) {
        UserAvatar(person.user.name, size = 32.dp, avatar = person.user.avatar)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                person.user.name,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Text(
                pluralStringResource(R.plurals.match_in_common, person.sharedLikes, person.sharedLikes),
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** "Anna likes it too" / "Anna and 2 others like it too". */
@Composable
private fun peopleSentence(match: Match): String {
    val first = match.people.first().user.name
    val others = match.peopleCount - 1
    return if (others <= 0) stringResource(R.string.match_likes_too, first)
    else pluralStringResource(R.plurals.match_like_too_many, others, first, others)
}
