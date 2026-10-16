package com.example.movielist.ui.comments

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.Comment
import com.example.movielist.domain.model.MAX_COMMENT_LENGTH
import com.example.movielist.domain.model.RatingStats
import com.example.movielist.ui.theme.MovieButton
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface
import java.util.Locale

private val REPLY_INDENT = 28.dp

/**
 * Comments under a movie in details, with one level of replies. Plain rows rather than a
 * LazyColumn, because it sits inside the details screen's scrolling column; pages of 20 keep
 * that cheap.
 */
@Composable
fun CommentsSection(
    state: CommentsUiState,
    actions: CommentsActions,
    modifier: Modifier = Modifier,
    /** The viewer's rating of this movie as it is now; their own comments show it instead of the loaded one. */
    myRating: RatingStats? = null,
) {
    val scoreOf: (Comment) -> Int? = { comment ->
        if (comment.isMine && myRating != null) myRating.myScore else comment.authorScore
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.comments_title),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                ),
            color = MovieRed,
        )

        Spacer(modifier = Modifier.height(12.dp))

        CommentInput(
            text = state.draft,
            onTextChange = actions::onDraftChange,
            placeholder = stringResource(R.string.comments_hint),
            sendDescription = stringResource(R.string.comments_send),
            isSending = state.isPosting,
            canSend = state.canPost,
            onSend = actions::post,
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading ->
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.loadError != null ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.comments_load_error, state.loadError),
                        color = Color.LightGray,
                    )
                    TextButton(onClick = actions::retry) { Text(stringResource(R.string.retry), color = MovieRed) }
                }
            // No comments: just the input box above.
            state.comments.isEmpty() -> Unit
            else -> {
                state.comments.forEach { comment ->
                    CommentItem(comment, state, actions, scoreOf)
                    Thread(comment, state, actions, scoreOf)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (state.nextCursor != null) {
                    LoadMoreButton(
                        text = stringResource(R.string.comments_load_more),
                        isLoading = state.isLoadingMore,
                        onClick = actions::loadMore,
                    )
                }
            }
        }
    }

    if (state.confirmDeleteId != null) {
        AlertDialog(
            onDismissRequest = actions::cancelDelete,
            title = { Text(stringResource(R.string.comments_delete_title)) },
            text = { Text(stringResource(R.string.comments_delete_text)) },
            confirmButton = {
                TextButton(onClick = actions::confirmDelete) {
                    Text(stringResource(R.string.comments_delete), color = MovieRed)
                }
            },
            dismissButton = {
                TextButton(onClick = actions::cancelDelete) { Text(stringResource(R.string.comments_cancel)) }
            },
            containerColor = MovieSurface,
        )
    }
}

/** A comment or reply, or its edit box while it's being edited. */
@Composable
private fun CommentItem(
    comment: Comment,
    state: CommentsUiState,
    actions: CommentsActions,
    scoreOf: (Comment) -> Int?,
) {
    if (comment.id == state.editingId) {
        EditingComment(
            text = state.editDraft,
            onTextChange = actions::onEditDraftChange,
            onSave = actions::saveEdit,
            onCancel = actions::cancelEdit,
        )
    } else {
        CommentCard(
            comment = comment,
            authorScore = scoreOf(comment),
            onReply = { actions.startReply(comment) },
            onEdit = { actions.startEdit(comment.id) },
            onDelete = { actions.askDelete(comment.id) },
        )
    }
}

/** The "View N replies" toggle under a top-level comment, and the replies when open. */
@Composable
private fun Thread(
    comment: Comment,
    state: CommentsUiState,
    actions: CommentsActions,
    scoreOf: (Comment) -> Int?,
) {
    val thread = state.thread(comment.id)
    val replyingHere = state.replyingTo?.threadId == comment.id
    val replyCount = maxOf(comment.replyCount, thread.replies.size)

    Column(modifier = Modifier.fillMaxWidth().padding(start = REPLY_INDENT)) {
        if (replyCount > 0) {
            TextButton(
                onClick = { actions.toggleReplies(comment.id) },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    text =
                        if (thread.expanded) stringResource(R.string.comments_hide_replies)
                        else pluralStringResource(R.plurals.comments_view_replies, replyCount, replyCount),
                    color = MovieRed,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (thread.expanded) {
            thread.replies.forEach { reply ->
                CommentItem(reply, state, actions, scoreOf)
                Spacer(modifier = Modifier.height(8.dp))
            }
            when {
                thread.isLoading && thread.replies.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp))
                thread.nextCursor != null ->
                    LoadMoreButton(
                        text = stringResource(R.string.comments_more_replies),
                        isLoading = thread.isLoading,
                        onClick = { actions.loadMoreReplies(comment.id) },
                    )
            }
        }

        if (replyingHere) {
            val target = state.replyingTo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.comments_replying_to, target?.authorName.orEmpty()),
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = actions::cancelReply) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.comments_cancel), tint = Color.Gray)
                }
            }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(target) { focusRequester.requestFocus() }
            CommentInput(
                text = state.replyDraft,
                onTextChange = actions::onReplyDraftChange,
                placeholder = stringResource(R.string.comments_reply_hint),
                sendDescription = stringResource(R.string.comments_send_reply),
                isSending = state.isReplying,
                canSend = state.canSendReply,
                onSend = actions::sendReply,
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
    }
}

@Composable
private fun LoadMoreButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        } else {
            TextButton(onClick = onClick) { Text(text, color = MovieRed) }
        }
    }
}

@Composable
private fun CommentInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    sendDescription: String,
    isSending: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Holds the cursor as well as the text. When the text changes from outside (the "@name "
    // a reply starts with, or clearing after sending), the cursor goes to the end.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (field.text != text) field = TextFieldValue(text, TextRange(text.length))

    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onTextChange(it.text)
        },
        placeholder = { Text(placeholder) },
        enabled = !isSending,
        maxLines = 5,
        supportingText =
            if (text.length > MAX_COMMENT_LENGTH * 0.8) {
                { Text(stringResource(R.string.comments_length, text.length, MAX_COMMENT_LENGTH)) }
            } else {
                null
            },
        trailingIcon = {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = sendDescription,
                        tint = if (canSend) MovieRed else Color.Gray,
                    )
                }
            }
        },
        colors = commentFieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CommentCard(
    comment: Comment,
    authorScore: Int?,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MovieSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp, end = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (comment.isMine) MovieRed else Color.White,
                    )
                    if (authorScore != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AuthorRating(authorScore)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = relativeTime(comment) +
                            if (comment.edited) " · " + stringResource(R.string.comments_edited) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = comment.body, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(
                        text = stringResource(R.string.comments_reply),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (comment.isMine) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.comments_more),
                            tint = Color.Gray,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.comments_edit)) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.comments_delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditingComment(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            maxLines = 6,
            colors = commentFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.comments_cancel), color = Color.LightGray) }
            TextButton(onClick = onSave, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.comments_save), color = MovieRed)
            }
        }
    }
}

/**
 * "just now", "5 minutes ago", "Yesterday", … in the phone's language. Anything under a
 * minute, or slightly in the future because the phone's clock is behind the server's, is "just now".
 */
@Composable
private fun relativeTime(comment: Comment): String {
    val now = System.currentTimeMillis()
    val created = comment.createdAt.toEpochMilli()
    if (now - created < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.comments_just_now)
    return DateUtils.getRelativeTimeSpanString(created, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
private fun commentFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.Gray,
        focusedBorderColor = MovieRed,
        unfocusedBorderColor = MovieButton,
        cursorColor = MovieRed,
        focusedContainerColor = MovieSurface,
        unfocusedContainerColor = MovieSurface,
        disabledContainerColor = MovieSurface,
    )

/** The author's rating of the movie on the five-star scale, e.g. "3.5★" for a score of 7. */
@Composable
private fun AuthorRating(score: Int) {
    val stars = if (score % 2 == 0) (score / 2).toString() else String.format(Locale.getDefault(), "%.1f", score / 2.0)
    val description = stringResource(R.string.comments_author_rating, stars)
    Text(
        text = "$stars★",
        style = MaterialTheme.typography.labelMedium,
        color = MovieRed,
        modifier = Modifier.semantics { contentDescription = description },
    )
}
