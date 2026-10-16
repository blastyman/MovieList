package com.example.movielist.ui.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.ui.components.MoviePosterItem
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieButton
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A user's profile. Your own (the "user" section) has the drawer menu, "edit name" and
 * "log out"; anyone else's (opened from search) has a back arrow.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRetry: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onMenuClick: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onEditName: () -> Unit,
    onNameDraftChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onCancelEditName: () -> Unit,
    onLogout: () -> Unit,
    friendActions: ProfileFriendActions,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (onMenuClick != null) {
            ScreenHeader("user", onMenuClick)
        } else if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back), tint = Color.White)
            }
        }

        val profile = state.profile
        when {
            profile != null -> ProfileContent(profile, state, onMovieClick, onEditName, onLogout, friendActions)
            state.error != null ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = Color.LightGray, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MovieRed) }
                }
            else ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
        }
    }

    if (state.confirmUnfriend) {
        AlertDialog(
            onDismissRequest = friendActions.cancelUnfriend,
            title = { Text(stringResource(R.string.friends_remove_title, state.profile?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = friendActions.confirmUnfriend) {
                    Text(stringResource(R.string.friends_remove), color = MovieRed)
                }
            },
            dismissButton = { TextButton(onClick = friendActions.cancelUnfriend) { Text(stringResource(R.string.comments_cancel)) } },
            containerColor = MovieSurface,
        )
    }

    val draft = state.nameDraft
    if (draft != null) {
        AlertDialog(
            onDismissRequest = onCancelEditName,
            title = { Text(stringResource(R.string.profile_edit_name)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onNameDraftChange,
                        singleLine = true,
                        placeholder = { Text(state.profile?.username.orEmpty()) },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MovieRed,
                                cursorColor = MovieRed,
                            ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.profile_edit_name_hint), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    state.message?.let { Text(it, color = MovieRed, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = onSaveName, enabled = !state.isSavingName) {
                    Text(stringResource(R.string.comments_save), color = MovieRed)
                }
            },
            dismissButton = { TextButton(onClick = onCancelEditName) { Text(stringResource(R.string.comments_cancel)) } },
            containerColor = MovieSurface,
        )
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    state: ProfileUiState,
    onMovieClick: (Movie) -> Unit,
    onEditName: () -> Unit,
    onLogout: () -> Unit,
    friendActions: ProfileFriendActions,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))
        UserAvatar(profile.name, size = 96.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(profile.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("@${profile.username}", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.profile_member_since, memberSince(profile)),
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!profile.isMe) {
            Spacer(modifier = Modifier.height(14.dp))
            FriendshipButton(profile.friendship, busy = state.isFriendBusy, actions = friendActions)
            if (state.message != null && state.nameDraft == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(state.message, color = MovieRed, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MovieSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(profile.stats.likes, stringResource(R.string.profile_stat_likes))
            Stat(profile.stats.ratings, stringResource(R.string.profile_stat_ratings))
            Stat(profile.stats.comments, stringResource(R.string.profile_stat_comments))
            Stat(profile.stats.friends, stringResource(R.string.profile_stat_friends))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(stringResource(R.string.profile_recent_likes), color = Color.White, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(12.dp))
    if (profile.recentLikes.isEmpty()) {
        Text(stringResource(R.string.profile_no_likes), color = Color.Gray)
    } else {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            profile.recentLikes.forEach { movie -> MoviePosterItem(movie) { onMovieClick(movie) } }
        }
    }

    if (profile.isMe) {
        Spacer(modifier = Modifier.height(28.dp))
        OutlinedButton(onClick = onEditName, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.profile_edit_name), color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MovieButton),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_log_out), color = MovieRed)
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
    }
}

/** e.g. "Oct 2026" in the phone's language. */
private fun memberSince(profile: UserProfile): String =
    profile.memberSince.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("LLL yyyy"))

/** What the friendship button can do; [ProfileViewModel] provides them. */
data class ProfileFriendActions(
    val add: () -> Unit,
    val accept: () -> Unit,
    val decline: () -> Unit,
    val cancelRequest: () -> Unit,
    val askUnfriend: () -> Unit,
    val confirmUnfriend: () -> Unit,
    val cancelUnfriend: () -> Unit,
)

/** The one thing you can do next with this person, as a button (or two, to answer a request). */
@Composable
private fun FriendshipButton(state: FriendshipState, busy: Boolean, actions: ProfileFriendActions) {
    if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MovieRed)
        return
    }
    when (state) {
        FriendshipState.NONE ->
            Button(onClick = actions.add, colors = ButtonDefaults.buttonColors(containerColor = MovieRed)) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.friends_add), color = Color.White)
            }
        FriendshipState.REQUEST_SENT ->
            OutlinedButton(onClick = actions.cancelRequest) {
                Text(stringResource(R.string.friends_request_sent_cancel), color = Color.LightGray)
            }
        FriendshipState.REQUEST_RECEIVED ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.accept, colors = ButtonDefaults.buttonColors(containerColor = MovieRed)) {
                    Text(stringResource(R.string.friends_accept), color = Color.White)
                }
                OutlinedButton(onClick = actions.decline) {
                    Text(stringResource(R.string.friends_decline), color = Color.LightGray)
                }
            }
        FriendshipState.FRIENDS ->
            OutlinedButton(onClick = actions.askUnfriend) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MovieRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.friendship_friends), color = Color.White)
            }
    }
}
