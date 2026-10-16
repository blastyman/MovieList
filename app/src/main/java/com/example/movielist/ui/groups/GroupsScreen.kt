package com.example.movielist.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.Group
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

/** The groups you're in, newest first, with a button to start a new one. */
@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onRetry: () -> Unit,
    onGroupClick: (Group) -> Unit,
    onNewGroup: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            ScreenHeader("groups", onMenuClick)

            when {
                state.error != null && state.groups.isEmpty() ->
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = Color.LightGray, textAlign = TextAlign.Center)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MovieRed) }
                    }
                !state.loaded ->
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.groups.isEmpty() ->
                    Text(
                        text = stringResource(R.string.groups_empty),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                    )
                else ->
                    LazyColumn(
                        // Room at the bottom so the button never covers the last group.
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.groups, key = { it.id }) { group -> GroupCard(group) { onGroupClick(group) } }
                    }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewGroup,
            containerColor = MovieRed,
            contentColor = Color.White,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.groups_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }
}

@Composable
private fun GroupCard(group: Group, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MovieSurface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.groups_count, group.members.size, group.maxMembers),
                    color = if (group.isFull) MovieRed else Color.Gray,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            MemberAvatars(group)
        }
    }
}

/** Everyone's avatar, overlapping like a little crowd. */
@Composable
fun MemberAvatars(group: Group, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        group.members.forEachIndexed { index, member ->
            UserAvatar(
                member.name,
                size = 34.dp,
                avatar = member.avatar,
                modifier =
                    Modifier.offset(x = (-10 * index).dp)
                        .border(2.dp, MovieSurface, CircleShape)
                        .background(MovieSurface, CircleShape),
            )
        }
    }
}
