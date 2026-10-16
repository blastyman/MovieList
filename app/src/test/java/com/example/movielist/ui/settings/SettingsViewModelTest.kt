package com.example.movielist.ui.settings

import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.model.Avatar
import com.example.movielist.domain.model.FriendRequestsFrom
import com.example.movielist.domain.model.Privacy
import com.example.movielist.domain.repository.AccountSettings
import com.example.movielist.domain.repository.InMemoryAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One account in memory; "taken" usernames and the password behave like the server. */
private class FakeAccount : AccountSettings {
    override val account = MutableStateFlow<Account?>(Account("1", "stelios", "s@example.com", null))
    var password = "correct-horse"
    val taken = setOf("anna")
    var uploads = 0
    var deleted = false

    private fun change(transform: (Account) -> Account): Result<Account> {
        val updated = transform(account.value!!)
        account.value = updated
        return Result.success(updated)
    }

    private fun wrongPassword() = Result.failure<Nothing>(AuthException(AuthError.WrongPassword))

    override suspend fun updateProfile(displayName: String?, username: String?, avatarEmoji: String?, avatarColor: String?): Result<Account> {
        if (username != null && username in taken) return Result.failure(AuthException(AuthError.UsernameTaken))
        return change { current ->
            current.copy(
                displayName = displayName?.ifEmpty { null } ?: current.displayName.takeIf { displayName == null },
                username = username ?: current.username,
                avatar = current.avatar.copy(
                    emoji = if (avatarEmoji != null) avatarEmoji.ifEmpty { null } else current.avatar.emoji,
                    color = if (avatarColor != null) avatarColor.ifEmpty { null } else current.avatar.color,
                ),
            )
        }
    }

    override suspend fun updatePrivacy(privacy: Privacy) = change { it.copy(privacy = privacy) }

    override suspend fun changeEmail(email: String, password: String) =
        if (password != this.password) wrongPassword() else change { it.copy(email = email) }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        if (currentPassword != password) return wrongPassword()
        password = newPassword
        return Result.success(Unit)
    }

    override suspend fun uploadAvatar(jpeg: ByteArray): Result<Account> {
        uploads++
        return change { it.copy(avatar = it.avatar.copy(photoUrl = "http://server/avatars/$uploads.jpg")) }
    }

    override suspend fun removeAvatar() = change { it.copy(avatar = it.avatar.copy(photoUrl = null)) }

    override suspend fun deleteAccount(password: String): Result<Unit> {
        if (password != this.password) return wrongPassword()
        deleted = true
        account.value = null
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val account = FakeAccount()
    private val appSettings = InMemoryAppSettings()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(account, appSettings)

    @Test
    fun changingTheHandleChecksItAndSaysWhenItIsTaken() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.open(SettingsDialog.HANDLE)

            viewModel.saveHandle("no spaces")
            assertEquals("3–30 characters: letters, numbers, '.' or '_'.", viewModel.uiState.edit.dialogError)

            viewModel.saveHandle("anna")
            advanceUntilIdle()
            assertEquals("That username is already taken.", viewModel.uiState.edit.dialogError)
            assertEquals(SettingsDialog.HANDLE, viewModel.uiState.edit.dialog) // stays open

            viewModel.saveHandle("@stelios_a") // a leading @ is fine
            advanceUntilIdle()
            assertEquals("stelios_a", viewModel.uiState.account?.username)
            assertNull(viewModel.uiState.edit.dialog)
        }

    @Test
    fun passwordChangesNeedTheRightPasswordAndAMatchingRepeat() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.open(SettingsDialog.PASSWORD)

            viewModel.savePassword("correct-horse", "battery-staple", "battery-stapel")
            assertEquals("The two new passwords don't match.", viewModel.uiState.edit.dialogError)

            viewModel.savePassword("wrong", "battery-staple", "battery-staple")
            advanceUntilIdle()
            assertEquals("That password isn't right.", viewModel.uiState.edit.dialogError)

            viewModel.savePassword("correct-horse", "battery-staple", "battery-staple")
            advanceUntilIdle()
            assertEquals("battery-staple", account.password)
            assertEquals("Password changed.", viewModel.uiState.edit.message)
        }

    @Test
    fun theAvatarCanBeAPhotoOrAnEmojiOnAColour() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.open(SettingsDialog.EMOJI)
            viewModel.pickEmoji("🍿")
            viewModel.pickColor("#1E88E5")
            advanceUntilIdle()
            assertEquals(Avatar(emoji = "🍿", color = "#1E88E5"), viewModel.uiState.account?.avatar)
            assertNull(viewModel.uiState.edit.dialog) // picking closes the picker

            viewModel.uploadPhoto(byteArrayOf(1, 2, 3))
            assertTrue(viewModel.uiState.edit.uploadingPhoto)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.edit.uploadingPhoto)
            assertEquals("http://server/avatars/1.jpg", viewModel.uiState.account?.avatar?.photoUrl)

            viewModel.removePhoto()
            advanceUntilIdle()
            assertNull(viewModel.uiState.account?.avatar?.photoUrl)
            assertEquals("🍿", viewModel.uiState.account?.avatar?.emoji) // the emoji is back
        }

    @Test
    fun privacyChangesOneSettingAndKeepsTheRest() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setPrivateLikes(true)
            advanceUntilIdle()
            viewModel.setFriendRequestsFrom(FriendRequestsFrom.MATCHES)
            advanceUntilIdle()

            assertEquals(
                Privacy(privateLikes = true, friendRequestsFrom = FriendRequestsFrom.MATCHES),
                viewModel.uiState.account?.privacy,
            )
        }

    @Test
    fun appSettingsAreSavedOnThePhone() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.setSwipeRightToLike(true)
            viewModel.setMatchPopups(false)
            advanceUntilIdle()

            assertTrue(appSettings.settings.value.swipeRightToLike)
            assertFalse(appSettings.settings.value.matchPopups)
            assertTrue(viewModel.uiState.app.autoplayTrailers) // untouched
        }

    @Test
    fun deletingTheAccountNeedsThePassword() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.open(SettingsDialog.DELETE)

            viewModel.deleteAccount("")
            assertEquals("Enter your password to confirm.", viewModel.uiState.edit.dialogError)
            viewModel.deleteAccount("wrong")
            advanceUntilIdle()
            assertFalse(account.deleted)

            viewModel.deleteAccount("correct-horse")
            advanceUntilIdle()
            assertTrue(account.deleted)
            assertTrue(viewModel.uiState.edit.deleted)
        }
}
