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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieRed

/** Name the group, tick friends, watch the counter, create. */
@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onToggle: (UserSummary) -> Unit,
    onRetryFriends: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back), tint = Color.White)
            }
            Text(stringResource(R.string.groups_create_title), color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.groups_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.groups_pick_friends),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.groups_count, state.memberCount, MAX_GROUP_MEMBERS),
                color = if (state.isFull) MovieRed else Color.Gray,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loadingFriends ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
                state.friendsError != null ->
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.friendsError, color = Color.LightGray, textAlign = TextAlign.Center)
                        TextButton(onClick = onRetryFriends) { Text(stringResource(R.string.retry), color = MovieRed) }
                    }
                state.friends.isEmpty() ->
                    Text(
                        stringResource(R.string.groups_no_friends),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 24.dp, end = 24.dp),
                    )
                else ->
                    FriendPicker(
                        friends = state.friends,
                        isPicked = { it.id in state.selected },
                        // Once full, only ticked friends stay tappable (to untick them).
                        isEnabled = { it.id in state.selected || !state.isFull },
                        onToggle = onToggle,
                    )
            }
        }

        Button(
            onClick = onCreate,
            enabled = state.canCreate,
            colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (state.isCreating) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text(stringResource(R.string.groups_create), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Friends with a checkbox each; shared by "new group" and "add friends". */
@Composable
fun FriendPicker(
    friends: List<UserSummary>,
    isPicked: (UserSummary) -> Boolean,
    isEnabled: (UserSummary) -> Boolean,
    onToggle: (UserSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(friends, key = { it.id }) { friend ->
            val enabled = isEnabled(friend)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = enabled) { onToggle(friend) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                UserAvatar(friend.name)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(friend.name, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.titleMedium)
                    Text("@${friend.username}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(
                    checked = isPicked(friend),
                    onCheckedChange = { onToggle(friend) },
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = MovieRed),
                )
            }
        }
    }
}
