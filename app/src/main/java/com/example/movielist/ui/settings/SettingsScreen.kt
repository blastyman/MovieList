package com.example.movielist.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.movielist.R
import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.FriendRequestsFrom
import com.example.movielist.domain.model.MessagesFrom
import com.example.movielist.ui.components.AvatarEditor
import com.example.movielist.ui.components.AvatarEditorActions
import com.example.movielist.ui.components.UserAvatar
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

/** Your profile picture and names, account, privacy, and how the app behaves. */
@Composable
fun SettingsScreen(state: SettingsUiState, viewModel: SettingsViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val account = state.account
    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back), tint = Color.White)
            }
            Text(stringResource(R.string.settings_title), color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        if (account == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        ProfileSection(account, state, viewModel)
        Section(stringResource(R.string.settings_account)) {
            NavRow(stringResource(R.string.settings_email), account.email) { viewModel.open(SettingsDialog.EMAIL) }
            NavRow(stringResource(R.string.settings_password), "••••••••") { viewModel.open(SettingsDialog.PASSWORD) }
            NavRow(stringResource(R.string.account_log_out), null, onClick = onLogout)
            NavRow(stringResource(R.string.settings_delete), null, danger = true) { viewModel.open(SettingsDialog.DELETE) }
        }
        PrivacySection(account, viewModel)
        Section(stringResource(R.string.settings_app)) {
            SwitchRow(stringResource(R.string.settings_swipe_right), stringResource(R.string.settings_swipe_right_hint), state.app.swipeRightToLike, viewModel::setSwipeRightToLike)
            SwitchRow(stringResource(R.string.settings_autoplay), null, state.app.autoplayTrailers, viewModel::setAutoplayTrailers)
            SwitchRow(stringResource(R.string.settings_match_popups), stringResource(R.string.settings_match_popups_hint), state.app.matchPopups, viewModel::setMatchPopups)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (account != null) SettingsDialogs(account, state.edit, viewModel)
}

@Composable
private fun ProfileSection(account: Account, state: SettingsUiState, viewModel: SettingsViewModel) {
    AvatarEditor(
        avatar = account.avatar,
        uploading = state.edit.uploadingPhoto,
        actions =
            AvatarEditorActions(
                uploadPhoto = viewModel::uploadPhoto,
                photoUnreadable = viewModel::photoUnreadable,
                removePhoto = viewModel::removePhoto,
                pickEmoji = viewModel::pickEmoji,
                removeEmoji = viewModel::removeEmoji,
                pickColor = viewModel::pickColor,
            ),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        UserAvatar(account.name, size = 104.dp, avatar = account.avatar)
    }

    Section(stringResource(R.string.settings_profile)) {
        NavRow(stringResource(R.string.settings_display_name), account.displayName ?: stringResource(R.string.settings_not_set)) {
            viewModel.open(SettingsDialog.DISPLAY_NAME)
        }
        NavRow(stringResource(R.string.settings_handle), "@${account.username}") { viewModel.open(SettingsDialog.HANDLE) }
    }
}

@Composable
private fun PrivacySection(account: Account, viewModel: SettingsViewModel) {
    val privacy = account.privacy
    Section(stringResource(R.string.settings_privacy)) {
        SwitchRow(stringResource(R.string.settings_show_in_matches), stringResource(R.string.settings_show_in_matches_hint), privacy.showInMatches, viewModel::setShowInMatches)
        SwitchRow(stringResource(R.string.settings_private_likes), stringResource(R.string.settings_private_likes_hint), privacy.privateLikes, viewModel::setPrivateLikes)
        ChoiceGroup(
            title = stringResource(R.string.settings_who_can_add),
            options = listOf(
                FriendRequestsFrom.EVERYONE to stringResource(R.string.settings_everyone),
                FriendRequestsFrom.MATCHES to stringResource(R.string.settings_matches_only),
                FriendRequestsFrom.NOBODY to stringResource(R.string.settings_nobody),
            ),
            selected = privacy.friendRequestsFrom,
            onSelect = viewModel::setFriendRequestsFrom,
        )
        ChoiceGroup(
            title = stringResource(R.string.settings_who_can_message),
            options = listOf(
                MessagesFrom.FRIENDS to stringResource(R.string.settings_friends),
                MessagesFrom.NOBODY to stringResource(R.string.settings_nobody),
            ),
            selected = privacy.messagesFrom,
            onSelect = viewModel::setMessagesFrom,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(title, color = MovieRed, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MovieSurface),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun NavRow(title: String, value: String?, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, color = if (danger) MovieRed else Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
        if (!danger) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .selectable(selected = checked, role = Role.Switch) { onChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row toggles
            colors = SwitchDefaults.colors(checkedTrackColor = MovieRed),
        )
    }
}

@Composable
private fun <T> ChoiceGroup(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
    options.forEach { (value, label) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 8.dp),
        ) {
            RadioButton(selected = value == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MovieRed), modifier = Modifier.padding(12.dp))
            Text(label, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsDialogs(account: Account, edit: SettingsEditState, viewModel: SettingsViewModel) {
    when (edit.dialog) {
        SettingsDialog.DISPLAY_NAME ->
            TextDialog(
                title = stringResource(R.string.settings_display_name),
                hint = stringResource(R.string.profile_edit_name_hint),
                fields = listOf(Field(stringResource(R.string.settings_display_name), account.displayName.orEmpty())),
                edit = edit,
                onDismiss = viewModel::closeDialog,
                onSave = { viewModel.saveDisplayName(it[0]) },
            )
        SettingsDialog.HANDLE ->
            TextDialog(
                title = stringResource(R.string.settings_handle),
                hint = stringResource(R.string.settings_handle_hint),
                fields = listOf(Field(stringResource(R.string.auth_username), account.username)),
                edit = edit,
                onDismiss = viewModel::closeDialog,
                onSave = { viewModel.saveHandle(it[0]) },
            )
        SettingsDialog.EMAIL ->
            TextDialog(
                title = stringResource(R.string.settings_email),
                hint = null,
                fields = listOf(
                    Field(stringResource(R.string.settings_new_email), account.email, KeyboardType.Email),
                    Field(stringResource(R.string.settings_current_password), "", KeyboardType.Password, secret = true),
                ),
                edit = edit,
                onDismiss = viewModel::closeDialog,
                onSave = { viewModel.saveEmail(it[0], it[1]) },
            )
        SettingsDialog.PASSWORD ->
            TextDialog(
                title = stringResource(R.string.settings_password),
                hint = null,
                fields = listOf(
                    Field(stringResource(R.string.settings_current_password), "", KeyboardType.Password, secret = true),
                    Field(stringResource(R.string.settings_new_password), "", KeyboardType.Password, secret = true),
                    Field(stringResource(R.string.settings_repeat_password), "", KeyboardType.Password, secret = true),
                ),
                edit = edit,
                onDismiss = viewModel::closeDialog,
                onSave = { viewModel.savePassword(it[0], it[1], it[2]) },
            )
        SettingsDialog.DELETE ->
            TextDialog(
                title = stringResource(R.string.settings_delete_title),
                hint = stringResource(R.string.settings_delete_text),
                fields = listOf(Field(stringResource(R.string.settings_current_password), "", KeyboardType.Password, secret = true)),
                edit = edit,
                confirm = stringResource(R.string.settings_delete),
                onDismiss = viewModel::closeDialog,
                onSave = { viewModel.deleteAccount(it[0]) },
            )
        null -> Unit
    }
}

private data class Field(val label: String, val initial: String, val keyboard: KeyboardType = KeyboardType.Text, val secret: Boolean = false)

/** A dialog with a few text fields; [onSave] gets their values in order. */
@Composable
private fun TextDialog(
    title: String,
    hint: String?,
    fields: List<Field>,
    edit: SettingsEditState,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    confirm: String = stringResource(R.string.comments_save),
) {
    var values by remember { mutableStateOf(fields.map { it.initial }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hint?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { text -> values = values.toMutableList().also { it[index] = text } },
                        label = { Text(field.label) },
                        singleLine = true,
                        enabled = !edit.saving,
                        keyboardOptions = KeyboardOptions(keyboardType = field.keyboard),
                        visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }
                edit.dialogError?.let { Text(it, color = MovieRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values) }, enabled = !edit.saving) {
                if (edit.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MovieRed)
                else Text(confirm, color = MovieRed)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !edit.saving) { Text(stringResource(R.string.comments_cancel)) } },
        containerColor = MovieSurface,
    )
}
