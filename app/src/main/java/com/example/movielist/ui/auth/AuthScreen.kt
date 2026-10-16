package com.example.movielist.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movielist.R
import com.example.movielist.domain.model.AuthError
import com.example.movielist.ui.theme.MovieButton
import com.example.movielist.ui.theme.MovieRed
import com.example.movielist.ui.theme.MovieSurface

private val ErrorText = Color(0xFFFF6B6B)

@Composable
fun AuthScreen(factory: ViewModelProvider.Factory) {
    val viewModel: AuthViewModel = viewModel(factory = factory)
    val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
    val isLogin = state.mode == AuthMode.LOGIN

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "movielist",
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                    ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    stringResource(
                        if (isLogin) R.string.auth_welcome_back else R.string.auth_create_account
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray,
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (isLogin) {
                AuthTextField(
                    value = state.login,
                    onValueChange = viewModel::onLoginChange,
                    label = stringResource(R.string.auth_login_field),
                    keyboardType = KeyboardType.Email,
                )
            } else {
                AuthTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = stringResource(R.string.auth_username),
                )
                Spacer(modifier = Modifier.height(12.dp))
                AuthTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = stringResource(R.string.auth_email),
                    keyboardType = KeyboardType.Email,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                onDone = viewModel::submit,
            )

            AnimatedVisibility(visible = state.error != null) {
                Text(
                    text = state.error?.let { errorMessage(it) }.orEmpty(),
                    color = ErrorText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MovieRed,
                        disabledContainerColor = MovieRed.copy(alpha = 0.5f),
                    ),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = stringResource(if (isLogin) R.string.auth_log_in else R.string.auth_sign_up),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = viewModel::toggleMode, enabled = !state.isSubmitting) {
                Text(
                    text =
                        stringResource(
                            if (isLogin) R.string.auth_switch_to_register
                            else R.string.auth_switch_to_login
                        ),
                    color = Color.LightGray,
                )
            }
        }
    }
}

@Composable
private fun errorMessage(error: AuthFormError): String =
    when (error) {
        AuthFormError.EmptyLogin -> stringResource(R.string.auth_error_empty_login)
        AuthFormError.EmptyPassword -> stringResource(R.string.auth_error_empty_password)
        AuthFormError.InvalidUsername -> stringResource(R.string.auth_error_invalid_username)
        AuthFormError.InvalidEmail -> stringResource(R.string.auth_error_invalid_email)
        AuthFormError.PasswordTooShort -> stringResource(R.string.auth_error_password_too_short)
        AuthFormError.PasswordTooLong -> stringResource(R.string.auth_error_password_too_long)
        is AuthFormError.Remote ->
            when (val remote = error.error) {
                AuthError.InvalidCredentials -> stringResource(R.string.auth_error_invalid_credentials)
                AuthError.UsernameTaken -> stringResource(R.string.auth_error_username_taken)
                AuthError.EmailTaken -> stringResource(R.string.auth_error_email_taken)
                AuthError.WrongPassword -> stringResource(R.string.auth_error_wrong_password)
                AuthError.Network -> stringResource(R.string.auth_error_network)
                is AuthError.Server ->
                    remote.message.ifBlank { stringResource(R.string.auth_error_unknown) }
            }
    }

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        colors = authFieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_password)) },
        singleLine = true,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription =
                        stringResource(
                            if (visible) R.string.auth_hide_password else R.string.auth_show_password
                        ),
                    tint = Color.LightGray,
                )
            }
        },
        colors = authFieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun authFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = MovieRed,
        unfocusedBorderColor = MovieButton,
        focusedLabelColor = MovieRed,
        unfocusedLabelColor = Color.Gray,
        cursorColor = MovieRed,
        focusedContainerColor = MovieSurface,
        unfocusedContainerColor = MovieSurface,
    )
