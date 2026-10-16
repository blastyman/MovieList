package com.example.movielist.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.repository.AuthRepository
import com.example.movielist.domain.repository.UserRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userId: String? = null,
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** The edit-name dialog is open with this text. */
    val nameDraft: String? = null,
    val isSavingName: Boolean = false,
    /** Why saving the name failed; shown in the edit dialog, which stays open. */
    val message: String? = null,
)

/** One person's profile. Lives as long as its screen (a navigation entry). */
class ProfileViewModel(
    private val users: UserRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiStateFlow: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    val uiState: ProfileUiState
        get() = _uiState.value

    private var loadJob: Job? = null

    /** Loads [userId]'s profile; does nothing if it is already shown, unless [refresh]. */
    fun load(userId: String, refresh: Boolean = false) {
        if (!refresh && uiState.userId == userId && (uiState.profile != null || uiState.isLoading)) return
        loadJob?.cancel()
        _uiState.update { it.copy(userId = userId, isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val profile = users.profile(userId)
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, error = exception.toUserMessage()) }
            }
        }
    }

    fun retry() {
        uiState.userId?.let { load(it, refresh = true) }
    }

    fun startEditName() {
        val profile = uiState.profile?.takeIf { it.isMe } ?: return
        _uiState.update { it.copy(nameDraft = profile.displayName.orEmpty()) }
    }

    fun onNameDraftChange(text: String) {
        _uiState.update { it.copy(nameDraft = text.take(MAX_DISPLAY_NAME), message = null) }
    }

    fun cancelEditName() {
        _uiState.update { it.copy(nameDraft = null, message = null) }
    }

    /** Saves the display name (blank removes it), then shows it on the profile. */
    fun saveName() {
        val draft = uiState.nameDraft ?: return
        if (uiState.isSavingName) return
        _uiState.update { it.copy(isSavingName = true) }
        viewModelScope.launch {
            auth.updateDisplayName(draft)
                .onSuccess { account ->
                    _uiState.update {
                        it.copy(
                            profile = it.profile?.copy(displayName = account.displayName),
                            nameDraft = null,
                            isSavingName = false,
                        )
                    }
                }
                .onFailure { error ->
                    val reason =
                        when (val authError = (error as? AuthException)?.error) {
                            is AuthError.Server -> authError.message
                            AuthError.Network -> "Can't reach the server. Check your connection."
                            else -> error.toUserMessage()
                        }
                    _uiState.update { it.copy(isSavingName = false, message = "Couldn't save your name. $reason") }
                }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private companion object {
        const val MAX_DISPLAY_NAME = 50
    }
}
