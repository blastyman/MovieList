package com.example.movielist.ui.auth

import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.model.SessionState
import com.example.movielist.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun invalidRegistrationNeverReachesTheServer() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(repository).apply { toggleMode() }

            viewModel.onUsernameChange("ab")
            viewModel.onEmailChange("a@b.co")
            viewModel.onPasswordChange("longenough")
            viewModel.submit()
            assertEquals(AuthFormError.InvalidUsername, viewModel.uiState.error)

            viewModel.onUsernameChange("valid_name")
            viewModel.onEmailChange("not-an-email")
            viewModel.submit()
            assertEquals(AuthFormError.InvalidEmail, viewModel.uiState.error)

            viewModel.onEmailChange("a@b.co")
            viewModel.onPasswordChange("short")
            viewModel.submit()
            assertEquals(AuthFormError.PasswordTooShort, viewModel.uiState.error)

            advanceUntilIdle()
            assertEquals(0, repository.calls)
        }

    @Test
    fun serverErrorIsShownAndSubmittingResets() =
        runTest(dispatcher) {
            val repository =
                FakeAuthRepository(
                    result = Result.failure(AuthException(AuthError.InvalidCredentials))
                )
            val viewModel = AuthViewModel(repository)

            viewModel.onLoginChange("someone")
            viewModel.onPasswordChange("wrong-password")
            viewModel.submit()
            assertTrue(viewModel.uiState.isSubmitting)

            advanceUntilIdle()
            assertFalse(viewModel.uiState.isSubmitting)
            assertEquals(
                AuthFormError.Remote(AuthError.InvalidCredentials),
                viewModel.uiState.error,
            )
        }

    @Test
    fun editingAFieldClearsTheErrorAndSwitchingModeClearsPassword() =
        runTest(dispatcher) {
            val viewModel = AuthViewModel(FakeAuthRepository())

            viewModel.submit()
            assertEquals(AuthFormError.EmptyLogin, viewModel.uiState.error)

            viewModel.onLoginChange("x")
            assertNull(viewModel.uiState.error)

            viewModel.onPasswordChange("secret-password")
            viewModel.toggleMode()
            assertEquals(AuthMode.REGISTER, viewModel.uiState.mode)
            assertEquals("", viewModel.uiState.password)
        }

    @Test
    fun successfulLoginCallsRepositoryOnceWithoutError() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(repository)

            viewModel.onLoginChange("someone")
            viewModel.onPasswordChange("right-password")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals(1, repository.calls)
            assertNull(viewModel.uiState.error)
            assertFalse(viewModel.uiState.isSubmitting)
        }

    private class FakeAuthRepository(
        private val result: Result<Account> =
            Result.success(Account("1", "someone", "someone@example.com", null, null))
    ) : AuthRepository {
        var calls = 0

        override val session: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedOut)
        override val account: StateFlow<Account?> = MutableStateFlow(null)

        override suspend fun register(username: String, email: String, password: String) =
            result.also { calls++ }

        override suspend fun login(login: String, password: String) = result.also { calls++ }

        override suspend fun refreshAccount() = result

        override suspend fun logout() = Unit
    }
}
