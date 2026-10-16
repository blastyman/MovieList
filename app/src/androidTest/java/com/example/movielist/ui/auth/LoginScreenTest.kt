package com.example.movielist.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.SessionState
import com.example.movielist.domain.repository.AuthRepository
import com.example.movielist.ui.theme.MovieListTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Remembers the login it was given and always lets it in. */
private class FakeAuth : AuthRepository {
    var loggedInWith: Pair<String, String>? = null
    override val session = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    override val account = MutableStateFlow<Account?>(null)

    override suspend fun login(login: String, password: String): Result<Account> {
        loggedInWith = login to password
        val account = Account("1", login, "$login@example.test", null, null)
        this.account.value = account
        session.value = SessionState.LoggedIn
        return Result.success(account)
    }

    override suspend fun register(username: String, email: String, password: String) = login(username, password)

    override suspend fun refreshAccount() = Result.failure<Account>(IllegalStateException("not used"))

    override suspend fun updateDisplayName(displayName: String) = Result.failure<Account>(IllegalStateException("not used"))

    override suspend fun logout() {
        session.value = SessionState.LoggedOut
    }
}

class LoginScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val auth = FakeAuth()
    private val factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(auth) as T
        }

    @Test
    fun emptyFormSaysWhatIsMissing() {
        composeRule.setContent { MovieListTheme { AuthScreen(factory) } }

        composeRule.onNodeWithText("log in").performClick()

        composeRule.onNodeWithText("Enter your username or email").assertIsDisplayed()
        assertEquals(null, auth.loggedInWith)
    }

    @Test
    fun loggingInSendsWhatWasTyped() {
        composeRule.setContent { MovieListTheme { AuthScreen(factory) } }

        composeRule.onNodeWithText("username or email").performTextInput("stelios")
        composeRule.onNodeWithText("password").performTextInput("correct-horse")
        composeRule.onNodeWithText("log in").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { auth.loggedInWith != null }
        assertEquals("stelios" to "correct-horse", auth.loggedInWith)
        assertEquals(SessionState.LoggedIn, auth.session.value)
    }
}
