package com.example.movielist.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

@Composable
fun FriendsScreen(
    state: FriendsUiState,
    onRetry: () -> Unit,
    onAccept: (UserSummary) -> Unit,
    onDecline: (UserSummary) -> Unit,
    onCancel: (UserSummary) -> Unit,
    onRemove: (UserSummary) -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onPersonClick: (UserSummary) -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader("friends", onMenuClick)

        val empty = state.friends.isEmpty() && state.incoming.isEmpty() && state.outgoing.isEmpty()
        when {
            state.loadError != null && empty ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.loadError, color = Color.LightGray, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MovieRed) }
                }
            state.isLoading && empty ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            empty ->
                Text(
                    stringResource(R.string.friends_empty),
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                )
            else -> {
                val requestsTitle = stringResource(R.string.friends_requests)
                val sentTitle = stringResource(R.string.friends_sent)
                val friendsTitle = stringResource(R.string.friends_title, state.friends.size)
                LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    section(requestsTitle, state.incoming.map { it.user }) { user ->
                        PersonRow(user, busy = user.id in state.busy, onClick = { onPersonClick(user) }) {
                            Button(
                                onClick = { onAccept(user) },
                                colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) { Text(stringResource(R.string.friends_accept), color = Color.White) }
                            TextButton(onClick = { onDecline(user) }) {
                                Text(stringResource(R.string.friends_decline), color = Color.Gray)
                            }
                        }
                    }
                    section(sentTitle, state.outgoing.map { it.user }) { user ->
                        PersonRow(user, busy = user.id in state.busy, onClick = { onPersonClick(user) }) {
                            TextButton(onClick = { onCancel(user) }) {
                                Text(stringResource(R.string.friends_cancel_request), color = Color.Gray)
                            }
                        }
                    }
                    section(friendsTitle, state.friends) { user ->
                        PersonRow(user, busy = user.id in state.busy, onClick = { onPersonClick(user) }) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.friends_options), tint = Color.Gray)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.friends_remove)) },
                                        onClick = {
                                            menuOpen = false
                                            onRemove(user)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.confirmRemove?.let { user ->
        AlertDialog(
            onDismissRequest = onCancelRemove,
            title = { Text(stringResource(R.string.friends_remove_title, user.name)) },
            confirmButton = {
                TextButton(onClick = onConfirmRemove) { Text(stringResource(R.string.friends_remove), color = MovieRed) }
            },
            dismissButton = { TextButton(onClick = onCancelRemove) { Text(stringResource(R.string.comments_cancel)) } },
            containerColor = MovieSurface,
        )
    }
}

/** A titled group of people; left out entirely when empty. */
private fun LazyListScope.section(title: String, people: List<UserSummary>, row: @Composable (UserSummary) -> Unit) {
    if (people.isEmpty()) return
    item {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    }
    items(people, key = { "$title:${it.id}" }) { row(it) }
}

@Composable
private fun PersonRow(
    user: UserSummary,
    busy: Boolean,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("@${user.username}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MovieRed)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
