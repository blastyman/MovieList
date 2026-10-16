package com.example.movielist.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.model.FriendRequestsFrom
import com.example.movielist.domain.model.MessagesFrom
import com.example.movielist.domain.model.Privacy
import com.example.movielist.domain.repository.AccountSettings
import com.example.movielist.domain.repository.AppSettings
import com.example.movielist.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The dialog that's open, if any. */
enum class SettingsDialog { DISPLAY_NAME, HANDLE, EMAIL, PASSWORD, DELETE, EMOJI }

/** What the settings screen shows besides the account and app settings themselves. */
data class SettingsEditState(
    val dialog: SettingsDialog? = null,
    /** A save is running; dialogs disable their buttons. */
    val saving: Boolean = false,
    val uploadingPhoto: Boolean = false,
    /** Shown inside the open dialog (wrong password, name taken …). */
    val dialogError: String? = null,
    /** A one-off note for the snackbar. */
    val message: String? = null,
    /** Set once the account is deleted; the app then shows the login screen by itself. */
    val deleted: Boolean = false,
)

data class SettingsUiState(
    val account: Account? = null,
    val app: AppSettings = AppSettings(),
    val edit: SettingsEditState = SettingsEditState(),
)

class SettingsViewModel(
    private val accountSettings: AccountSettings,
    private val appSettings: AppSettingsRepository,
) : ViewModel() {
    private val edit = MutableStateFlow(SettingsEditState())

    val uiStateFlow: StateFlow<SettingsUiState> =
        combine(accountSettings.account, appSettings.settings, edit) { account, app, edit -> SettingsUiState(account, app, edit) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(accountSettings.account.value, appSettings.settings.value))
    /** The same, read straight from the sources, so it's current right after a change. */
    val uiState: SettingsUiState
        get() = SettingsUiState(accountSettings.account.value, appSettings.settings.value, edit.value)

    fun open(dialog: SettingsDialog) = edit.update { it.copy(dialog = dialog, dialogError = null) }

    fun closeDialog() = edit.update { if (it.saving) it else it.copy(dialog = null, dialogError = null) }

    fun messageShown() = edit.update { it.copy(message = null) }

    // Profile

    fun saveDisplayName(name: String) {
        if (name.trim().length > MAX_DISPLAY_NAME) return dialogError("Names can be at most $MAX_DISPLAY_NAME characters.")
        save { accountSettings.updateProfile(displayName = name.trim()) }
    }

    fun saveHandle(handle: String) {
        val trimmed = handle.trim().removePrefix("@")
        if (!HANDLE.matches(trimmed)) return dialogError("3–30 characters: letters, numbers, '.' or '_'.")
        if (trimmed == uiState.account?.username) return closeDialog()
        save { accountSettings.updateProfile(username = trimmed) }
    }

    fun pickEmoji(emoji: String) = save { accountSettings.updateProfile(avatarEmoji = emoji) }

    fun removeEmoji() = quick { accountSettings.updateProfile(avatarEmoji = "") }

    /** [color] is `#RRGGBB`, or null for the default red. */
    fun pickColor(color: String?) = quick { accountSettings.updateProfile(avatarColor = color ?: "") }

    /** [jpeg] is already cropped square and shrunk by the screen. */
    fun uploadPhoto(jpeg: ByteArray) {
        edit.update { it.copy(uploadingPhoto = true) }
        viewModelScope.launch {
            val result = accountSettings.uploadAvatar(jpeg)
            edit.update { it.copy(uploadingPhoto = false, message = result.exceptionOrNull()?.let(::describe)) }
        }
    }

    fun photoUnreadable() = edit.update { it.copy(message = "Couldn't read that picture. Try another one.") }

    fun removePhoto() = quick { accountSettings.removeAvatar() }

    // Account

    fun saveEmail(email: String, password: String) {
        if (!EMAIL.matches(email.trim())) return dialogError("Enter a valid email address.")
        if (password.isEmpty()) return dialogError("Enter your password to confirm.")
        save { accountSettings.changeEmail(email, password) }
    }

    fun savePassword(current: String, new: String, repeat: String) {
        when {
            new.length < MIN_PASSWORD -> return dialogError("The new password needs at least $MIN_PASSWORD characters.")
            new.toByteArray().size > MAX_PASSWORD_BYTES -> return dialogError("That password is too long.")
            new != repeat -> return dialogError("The two new passwords don't match.")
        }
        save(done = "Password changed.") { accountSettings.changePassword(current, new) }
    }

    fun deleteAccount(password: String) {
        if (password.isEmpty()) return dialogError("Enter your password to confirm.")
        edit.update { it.copy(saving = true, dialogError = null) }
        viewModelScope.launch {
            accountSettings.deleteAccount(password)
                .onSuccess { edit.update { it.copy(saving = false, dialog = null, deleted = true) } }
                .onFailure { error -> edit.update { it.copy(saving = false, dialogError = describe(error)) } }
        }
    }

    // Privacy: saved on each tap; the switches show what the server stored.

    fun setShowInMatches(on: Boolean) = privacy { it.copy(showInMatches = on) }

    fun setPrivateLikes(on: Boolean) = privacy { it.copy(privateLikes = on) }

    fun setFriendRequestsFrom(value: FriendRequestsFrom) = privacy { it.copy(friendRequestsFrom = value) }

    fun setMessagesFrom(value: MessagesFrom) = privacy { it.copy(messagesFrom = value) }

    // App

    fun setSwipeRightToLike(on: Boolean) {
        viewModelScope.launch { appSettings.update { it.copy(swipeRightToLike = on) } }
    }

    fun setAutoplayTrailers(on: Boolean) {
        viewModelScope.launch { appSettings.update { it.copy(autoplayTrailers = on) } }
    }

    fun setMatchPopups(on: Boolean) {
        viewModelScope.launch { appSettings.update { it.copy(matchPopups = on) } }
    }

    private fun privacy(change: (Privacy) -> Privacy) {
        val current = uiState.account?.privacy ?: return
        quick { accountSettings.updatePrivacy(change(current)) }
    }

    /** A change from a dialog: errors show in the dialog, success closes it. */
    private fun save(done: String? = null, call: suspend () -> Result<*>) {
        edit.update { it.copy(saving = true, dialogError = null) }
        viewModelScope.launch {
            call()
                .onSuccess { edit.update { it.copy(saving = false, dialog = null, message = done) } }
                .onFailure { error -> edit.update { it.copy(saving = false, dialogError = describe(error)) } }
        }
    }

    /** A one-tap change: errors go to the snackbar. */
    private fun quick(call: suspend () -> Result<*>) {
        viewModelScope.launch {
            call().onFailure { error -> edit.update { it.copy(message = describe(error)) } }
        }
    }

    private fun dialogError(text: String) = edit.update { it.copy(dialogError = text) }

    private fun describe(error: Throwable): String =
        when (val auth = (error as? AuthException)?.error) {
            AuthError.UsernameTaken -> "That username is already taken."
            AuthError.EmailTaken -> "An account with that email already exists."
            AuthError.WrongPassword -> "That password isn't right."
            AuthError.Network -> "Can't reach the server. Check your connection."
            is AuthError.Server -> auth.message
            AuthError.InvalidCredentials, null -> error.message ?: "Something went wrong."
        }

    private companion object {
        const val MAX_DISPLAY_NAME = 50
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD_BYTES = 72
        val HANDLE = Regex("^[A-Za-z0-9._]{3,30}$")
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
