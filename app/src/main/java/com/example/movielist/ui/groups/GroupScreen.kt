package com.example.movielist.ui.groups

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.Group
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.ui.components.MoviePosterItem
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieButton
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

/** What the group screen can ask its ViewModel to do. */
class GroupActions(
    val retry: () -> Unit,
    val startAdding: () -> Unit,
    val stopAdding: () -> Unit,
    val add: (UserSummary) -> Unit,
    val askRemove: (UserSummary) -> Unit,
    val confirmRemove: () -> Unit,
    val cancelRemove: () -> Unit,
    val askLeave: () -> Unit,
    val confirmLeave: () -> Unit,
    val cancelLeave: () -> Unit,
    val startRename: () -> Unit,
    val onNameDraftChange: (String) -> Unit,
    val saveRename: () -> Unit,
    val cancelRename: () -> Unit,
)

/** One group: the movies everyone likes up top, then the members and what you can do. */
@Composable
fun GroupScreen(
    state: GroupUiState,
    myId: String?,
    actions: GroupActions,
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onPersonClick: (UserSummary) -> Unit,
) {
    val group = state.group
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back), tint = Color.White)
            }
            if (group != null) {
                Text(
                    group.name,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (group.isOwner(myId)) {
                    IconButton(onClick = actions.startRename) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.group_rename), tint = Color.Gray)
                    }
                }
            }
        }

        when {
            group != null -> GroupContent(group, state, myId, actions, onMovieClick, onPersonClick)
            state.error != null ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = Color.LightGray, textAlign = TextAlign.Center)
                    TextButton(onClick = actions.retry) { Text(stringResource(R.string.retry), color = MovieRed) }
                }
            else ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
        }
    }

    if (group != null) GroupDialogs(group, state, myId, actions)
}

@Composable
private fun GroupContent(
    group: Group,
    state: GroupUiState,
    myId: String?,
    actions: GroupActions,
    onMovieClick: (Movie) -> Unit,
    onPersonClick: (UserSummary) -> Unit,
) {
    val iOwnIt = group.isOwner(myId)
    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(stringResource(R.string.group_matches), color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(10.dp))
            when {
                group.members.size < 2 -> Text(stringResource(R.string.group_matches_alone), color = Color.Gray)
                state.matches.isEmpty() -> Text(stringResource(R.string.group_matches_empty), color = Color.Gray)
                else ->
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.matches, key = { it.movie.id }) { match ->
                            MoviePosterItem(match.movie) { onMovieClick(match.movie) }
                        }
                    }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.group_members, group.members.size, group.maxMembers),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(group.members, key = { it.id }) { member ->
            MemberRow(
                member = member,
                isOwner = group.isOwner(member.id),
                isMe = member.id == myId,
                busy = member.id in state.busy,
                canRemove = iOwnIt && member.id != myId,
                onClick = { onPersonClick(member) },
                onRemove = { actions.askRemove(member) },
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            if (group.isFull) {
                Text(
                    stringResource(R.string.group_full),
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(
                    onClick = actions.startAdding,
                    colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.group_add), color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = actions.askLeave,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.group_leave), color = MovieRed)
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: UserSummary,
    isOwner: Boolean,
    isMe: Boolean,
    busy: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !isMe, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(member.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("@${member.username}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        val tag = when {
            isOwner && isMe -> stringResource(R.string.group_owner) + " · " + stringResource(R.string.group_you)
            isOwner -> stringResource(R.string.group_owner)
            isMe -> stringResource(R.string.group_you)
            else -> null
        }
        tag?.let { Text(it, color = MovieRed, style = MaterialTheme.typography.labelMedium) }
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(24.dp), strokeWidth = 2.dp, color = MovieRed)
            canRemove -> {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.group_options), tint = Color.Gray)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.group_remove)) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDialogs(group: Group, state: GroupUiState, myId: String?, actions: GroupActions) {
    state.confirmRemove?.let { member ->
        AlertDialog(
            onDismissRequest = actions.cancelRemove,
            title = { Text(stringResource(R.string.group_remove_title, member.name)) },
            confirmButton = {
                TextButton(onClick = actions.confirmRemove) { Text(stringResource(R.string.group_remove), color = MovieRed) }
            },
            dismissButton = { TextButton(onClick = actions.cancelRemove) { Text(stringResource(R.string.comments_cancel)) } },
            containerColor = MovieSurface,
        )
    }

    if (state.confirmLeave) {
        // The owner is told what happens to the group without them.
        val ownerLeaving = group.isOwner(myId)
        AlertDialog(
            onDismissRequest = actions.cancelLeave,
            title = { Text(stringResource(R.string.group_leave_title, group.name)) },
            text = { Text(stringResource(if (ownerLeaving) R.string.group_leave_owner_text else R.string.group_leave_text)) },
            confirmButton = {
                TextButton(onClick = actions.confirmLeave) { Text(stringResource(R.string.group_leave), color = MovieRed) }
            },
            dismissButton = { TextButton(onClick = actions.cancelLeave) { Text(stringResource(R.string.comments_cancel)) } },
            containerColor = MovieSurface,
        )
    }

    state.nameDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = actions.cancelRename,
            title = { Text(stringResource(R.string.group_rename)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = actions.onNameDraftChange,
                    label = { Text(stringResource(R.string.groups_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = actions.saveRename, enabled = draft.isNotBlank()) {
                    Text(stringResource(R.string.comments_save), color = MovieRed)
                }
            },
            dismissButton = { TextButton(onClick = actions.cancelRename) { Text(stringResource(R.string.comments_cancel)) } },
            containerColor = MovieSurface,
        )
    }

    state.addable?.let { addable ->
        AlertDialog(
            onDismissRequest = actions.stopAdding,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.group_add), modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.groups_count, group.members.size, group.maxMembers),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            text = {
                when {
                    state.loadingAddable -> CircularProgressIndicator()
                    addable.isEmpty() -> Text(stringResource(R.string.group_add_none), color = Color.Gray)
                    else ->
                        LazyColumn(modifier = Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(addable, key = { it.id }) { friend ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    UserAvatar(friend.name, size = 36.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(friend.name, color = Color.White, modifier = Modifier.weight(1f))
                                    if (friend.id in state.busy) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MovieRed)
                                    } else {
                                        Button(
                                            onClick = { actions.add(friend) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MovieButton),
                                            contentPadding = PaddingValues(horizontal = 14.dp),
                                        ) { Text(stringResource(R.string.friends_add), color = Color.White) }
                                    }
                                }
                            }
                        }
                }
            },
            confirmButton = { TextButton(onClick = actions.stopAdding) { Text(stringResource(R.string.done), color = MovieRed) } },
            containerColor = MovieSurface,
        )
    }
}
