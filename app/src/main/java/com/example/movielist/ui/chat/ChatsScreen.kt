package com.example.movielist.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.Avatar
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.ConversationType
import com.example.movielist.ui.components.ScreenHeader
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieRed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Your chats, most recently active first, with the last message and an unread badge. */
@Composable
fun ChatsScreen(
    state: ChatsUiState,
    myId: String?,
    onRetry: () -> Unit,
    onChatClick: (Conversation) -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader("chats", onMenuClick)

        when {
            state.error != null && state.conversations.isEmpty() ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = Color.LightGray, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MovieRed) }
                }
            !state.loaded ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.conversations.isEmpty() ->
                Text(
                    stringResource(R.string.chats_empty),
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                )
            else ->
                LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        ConversationRow(conversation, myId) { onChatClick(conversation) }
                    }
                }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, myId: String?, onClick: () -> Unit) {
    val unread = conversation.unreadCount > 0
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(conversation)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(shortTime(conversation.updatedAt), color = if (unread) MovieRed else Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val last = conversation.lastMessage
                val preview = when {
                    last == null -> stringResource(R.string.chat_empty)
                    last.senderId == myId -> stringResource(R.string.chat_you, last.body)
                    conversation.type == ConversationType.GROUP ->
                        conversation.members.firstOrNull { it.id == last.senderId }?.let { "${it.name}: ${last.body}" } ?: last.body
                    else -> last.body
                }
                Text(
                    preview,
                    color = if (unread) Color.White else Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UnreadBadge(conversation.unreadCount)
                }
            }
        }
    }
}

@Composable
fun ConversationAvatar(conversation: Conversation, size: Dp = 48.dp) {
    if (conversation.type == ConversationType.GROUP) {
        Box(modifier = Modifier.size(size).background(MovieRed.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = MovieRed, modifier = Modifier.size(size * 0.55f))
        }
    } else {
        UserAvatar(conversation.title, size = size, avatar = conversation.members.firstOrNull()?.avatar ?: Avatar.NONE)
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val label = pluralStringResource(R.plurals.chat_unread, count, count)
    Box(
        modifier =
            Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                .background(MovieRed, CircleShape)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        )
    }
}

/** "14:32" today, "Mon 3 Oct"-style date before that. */
fun shortTime(instant: Instant): String {
    val local = instant.atZone(ZoneId.systemDefault())
    return if (local.toLocalDate() == LocalDate.now()) local.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    else local.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
}
