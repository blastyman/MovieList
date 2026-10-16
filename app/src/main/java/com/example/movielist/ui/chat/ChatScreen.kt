package com.example.movielist.ui.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.ConversationType
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

/** One chat: bubbles bottom-up, newest at the bottom, a typing line and the send box. */
@Composable
fun ChatScreen(
    state: ChatUiState,
    isConnected: Boolean,
    isMine: (ChatItem) -> Boolean,
    onBack: () -> Unit,
    onTitleClick: (Conversation) -> Unit,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetrySend: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val conversation = state.conversation
        TopBar(conversation, state, isConnected, onBack, onTitleClick)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                conversation == null && state.error != null ->
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = Color.LightGray, textAlign = TextAlign.Center)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MovieRed) }
                    }
                conversation == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.items.isEmpty() && !state.isLoading ->
                    Text(stringResource(R.string.chat_empty), color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                else -> Messages(conversation, state, isMine, onLoadOlder, onRetrySend)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                enabled = conversation != null,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MovieRed),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onSend,
                enabled = conversation != null && state.draft.isNotBlank(),
                modifier = Modifier.size(48.dp).background(if (state.draft.isNotBlank()) MovieRed else MovieSurface, RoundedCornerShape(50)),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = Color.White)
            }
        }
    }
}

@Composable
private fun TopBar(
    conversation: Conversation?,
    state: ChatUiState,
    isConnected: Boolean,
    onBack: () -> Unit,
    onTitleClick: (Conversation) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back), tint = Color.White)
        }
        if (conversation == null) return
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTitleClick(conversation) }.padding(4.dp),
        ) {
            ConversationAvatar(conversation, size = 40.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    conversation.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val typingNames = state.typing.mapNotNull { id -> conversation.members.firstOrNull { it.id == id }?.name }
                val status = when {
                    !isConnected -> stringResource(R.string.chat_offline)
                    typingNames.size == 1 -> stringResource(R.string.chat_typing_one, typingNames.single())
                    typingNames.size > 1 -> stringResource(R.string.chat_typing_many)
                    else -> null
                }
                status?.let { Text(it, color = if (isConnected) MovieRed else Color.Gray, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun Messages(
    conversation: Conversation,
    state: ChatUiState,
    isMine: (ChatItem) -> Boolean,
    onLoadOlder: () -> Unit,
    onRetrySend: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // Newest at index 0 at the bottom. Follow new messages when you're at the bottom anyway,
    // and always when you sent one.
    val newest = state.items.firstOrNull()
    LaunchedEffect(newest?.key) {
        if (newest != null && (isMine(newest) || listState.firstVisibleItemIndex <= 2)) listState.animateScrollToItem(0)
    }

    // Near the top of what's loaded: fetch older messages.
    val nearOldest by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearOldest, state.olderCursor) { if (nearOldest && state.olderCursor != null) onLoadOlder() }

    val isGroup = conversation.type == ConversationType.GROUP
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp),
        // Anchored to the bottom, like any chat, even when a few messages don't fill the screen.
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(state.items, key = { _, item -> item.key }) { index, item ->
            val mine = isMine(item)
            val older = state.items.getOrNull(index + 1)
            val sender = (item as? ChatItem.Sent)?.message?.senderId
            // In a group, name the sender above the first of their messages in a row.
            val senderName =
                if (isGroup && !mine && (older !is ChatItem.Sent || older.message.senderId != sender))
                    conversation.members.firstOrNull { it.id == sender }?.name
                else null
            Bubble(item, mine, senderName, onRetrySend)
        }
        if (state.loadingOlder) {
            item(key = "older") {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MovieRed)
                }
            }
        }
    }
}

@Composable
private fun Bubble(item: ChatItem, mine: Boolean, senderName: String?, onRetrySend: (String) -> Unit) {
    val failed = item is ChatItem.Pending && item.failed
    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth().padding(top = if (senderName != null) 6.dp else 0.dp),
    ) {
        senderName?.let {
            Text(it, color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
        }
        Box(
            modifier =
                Modifier.widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                    .background(if (mine) MovieRed else MovieSurface)
                    .then(if (failed) Modifier.clickable { onRetrySend((item as ChatItem.Pending).clientId) } else Modifier)
                    // Still on its way: a little faded until the server has it.
                    .alpha(if (item is ChatItem.Pending && !failed) 0.6f else 1f)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(item.body, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = if (failed) stringResource(R.string.chat_failed) else shortTime(item.createdAt),
            color = if (failed) MovieRed else Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
