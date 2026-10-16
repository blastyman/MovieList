package com.example.movielist.ui.components

import android.view.ContextThemeWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.emoji2.emojipicker.EmojiPickerView
import com.example.movielist.R
import com.example.movielist.domain.model.Avatar
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface
import kotlinx.coroutines.launch

/** What the avatar editor can ask for; the screen's ViewModel provides them. */
class AvatarEditorActions(
    /** [jpeg] is already cropped square and shrunk. */
    val uploadPhoto: (jpeg: ByteArray) -> Unit,
    val photoUnreadable: () -> Unit,
    val removePhoto: () -> Unit,
    val pickEmoji: (String) -> Unit,
    val removeEmoji: () -> Unit,
    /** `#RRGGBB`, or null for the default. */
    val pickColor: (String?) -> Unit,
)

/**
 * Changes a picture in a circle, for a person or a group: [preview] on top (the circle itself),
 * then a photo from the gallery or an emoji from Android's picker, a way to remove it, and the
 * colours behind an emoji or the fallback.
 */
@Composable
fun AvatarEditor(
    avatar: Avatar,
    uploading: Boolean,
    actions: AvatarEditorActions,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickingEmoji by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val jpeg = avatarPhotoJpeg(context, uri)
                if (jpeg == null) actions.photoUnreadable() else actions.uploadPhoto(jpeg)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            preview()
            if (uploading) CircularProgressIndicator(color = Color.White)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_photo), color = Color.White)
            }
            OutlinedButton(onClick = { pickingEmoji = true }) {
                Text(avatar.emoji ?: "😀")
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_emoji), color = Color.White)
            }
        }
        if (avatar.photoUrl != null || avatar.emoji != null) {
            TextButton(onClick = { if (avatar.photoUrl != null) actions.removePhoto() else actions.removeEmoji() }) {
                Text(
                    stringResource(if (avatar.photoUrl != null) R.string.settings_remove_photo else R.string.settings_remove_emoji),
                    color = Color.Gray,
                )
            }
        }
        // Behind the emoji or the fallback (a photo covers it). Wraps in a narrow dialog.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            AVATAR_COLORS.forEach { hex ->
                val selected = (avatar.color ?: AVATAR_COLORS.first()).equals(hex, ignoreCase = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.size(26.dp)
                            .clip(CircleShape)
                            .background(parseColor(hex) ?: MovieRed)
                            .border(if (selected) 2.dp else 0.dp, Color.White, CircleShape)
                            .clickable(onClickLabel = hex) { actions.pickColor(if (hex == AVATAR_COLORS.first()) null else hex) },
                ) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (pickingEmoji) {
        Dialog(onDismissRequest = { pickingEmoji = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = MovieSurface), shape = RoundedCornerShape(20.dp)) {
                Text(
                    stringResource(R.string.settings_pick_emoji),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                AndroidView(
                    factory = { viewContext ->
                        // A classic View; the dark theme makes it match the app.
                        EmojiPickerView(ContextThemeWrapper(viewContext, android.R.style.Theme_DeviceDefault)).apply {
                            setOnEmojiPickedListener {
                                pickingEmoji = false
                                actions.pickEmoji(it.emoji)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            }
        }
    }
}

/** Behind an emoji or the fallback. The first is the app's red, the default. */
private val AVATAR_COLORS = listOf("#E50914", "#F57C00", "#FBC02D", "#43A047", "#00897B", "#1E88E5", "#5E35B1", "#D81B60", "#546E7A")
