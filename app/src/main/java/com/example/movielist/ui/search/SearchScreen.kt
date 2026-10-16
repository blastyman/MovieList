package com.example.movielist.ui.search

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.ui.components.MovieImage
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieButton
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onTabSelected: (SearchTab) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onPersonClick: (UserSummary) -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader("search", onMenuClick)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear), tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(14.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MovieRed,
                    unfocusedBorderColor = MovieButton,
                    cursorColor = MovieRed,
                    focusedContainerColor = MovieSurface,
                    unfocusedContainerColor = MovieSurface,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(state.tab.ordinal),
                    color = MovieRed,
                    width = 64.dp,
                )
            },
        ) {
            SearchTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            stringResource(if (tab == SearchTab.MOVIES) R.string.search_tab_movies else R.string.search_tab_people),
                            color = if (state.tab == tab) Color.White else Color.Gray,
                        )
                    },
                )
            }
        }

        val query = state.searchedQuery
        when {
            query.isEmpty() -> Message(stringResource(R.string.search_start))
            state.tab == SearchTab.PEOPLE && query.length < SearchViewModel.MIN_PEOPLE_QUERY ->
                Message(stringResource(R.string.search_people_min))
            state.tab == SearchTab.MOVIES ->
                ResultList(state.movies, query, onLoadMore, onRetry) { movie -> MovieRow(movie) { onMovieClick(movie) } }
            else ->
                ResultList(state.people, query, onLoadMore, onRetry) { person -> PersonRow(person) { onPersonClick(person) } }
        }
    }
}

@Composable
private fun <T> ResultList(
    results: SearchResults<T>,
    query: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        results.error != null && results.items.isEmpty() ->
            Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(results.error, color = Color.LightGray, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MovieRed) }
            }
        results.isLoading && results.items.isEmpty() ->
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        results.items.isEmpty() -> Message(stringResource(R.string.search_nothing, query))
        else ->
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(results.items) { row(it) }
                loadMoreItem(results.hasMore, onLoadMore)
            }
    }
}

/** At the end of the list: loads the next page as soon as it scrolls into view. */
private fun LazyListScope.loadMoreItem(hasMore: Boolean, onLoadMore: () -> Unit) {
    if (!hasMore) return
    item {
        LaunchedEffect(Unit) { onLoadMore() }
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MovieRow(movie: Movie, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovieImage(
            posterPath = movie.posterPath,
            contentDescription = movie.title,
            modifier = Modifier.width(64.dp).height(96.dp).clip(RoundedCornerShape(10.dp)),
            width = 185,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(movie.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (movie.overview.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(movie.overview, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PersonRow(person: UserSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(person.name, avatar = person.avatar)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("@${person.username}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        friendshipLabel(person.friendship)?.let { label ->
            Text(label, color = MovieRed, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** "Friends", "Request sent", "Wants to be friends", or null when there's no relation. */
@Composable
fun friendshipLabel(state: FriendshipState): String? =
    when (state) {
        FriendshipState.NONE -> null
        FriendshipState.FRIENDS -> stringResource(R.string.friendship_friends)
        FriendshipState.REQUEST_SENT -> stringResource(R.string.friendship_request_sent)
        FriendshipState.REQUEST_RECEIVED -> stringResource(R.string.friendship_request_received)
    }

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        color = Color.Gray,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp),
    )
}
