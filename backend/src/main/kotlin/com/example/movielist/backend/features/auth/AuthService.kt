package com.example.movielist.backend.features.auth

import com.example.movielist.backend.db.violatedUniqueConstraint
import com.example.movielist.backend.features.users.UserRecord
import com.example.movielist.backend.features.users.UserRepository
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.ConflictException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.net.URI
import java.util.UUID

class InvalidCredentialsException :
    ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Wrong username/email or password")

class AuthService(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val jwt: JwtService,
) {

    suspend fun register(request: RegisterRequest): AuthResponse {
        val username = request.username.trim()
        val email = request.email.trim()
        validateUsername(username)
        validateEmail(email)
        validatePassword(request.password)

        // Friendly errors for the common case; the unique indexes still guard against races.
        if (users.existsByUsername(username)) throw usernameTaken()
        if (users.existsByEmail(email)) throw emailTaken()

        val user = try {
            users.create(username, email, hasher.hash(request.password))
        } catch (e: ExposedSQLException) {
            when (e.violatedUniqueConstraint()) {
                "users_username_lower_key" -> throw usernameTaken()
                "users_email_lower_key" -> throw emailTaken()
                else -> throw e
            }
        }
        return AuthResponse(token = jwt.issueToken(user.id), user = user.toDto())
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val user = users.findByLogin(request.login.trim())
        if (user == null) {
            hasher.verify(request.password, hasher.dummyHash)
            throw InvalidCredentialsException()
        }
        if (!hasher.verify(request.password, user.passwordHash)) throw InvalidCredentialsException()
        return AuthResponse(token = jwt.issueToken(user.id), user = user.toDto())
    }

    suspend fun me(userId: UUID): CurrentUserDto =
        users.findById(userId)?.toDto() ?: throw NotFoundException("User no longer exists")

    /** A blank string clears the field; a missing field leaves it unchanged. */
    suspend fun updateMe(userId: UUID, request: UpdateMeRequest): CurrentUserDto {
        val displayName = request.displayName?.trim()
        val avatarUrl = request.avatarUrl?.trim()

        if (!displayName.isNullOrEmpty() && displayName.length > 50) {
            throw ValidationException("Display name must be at most 50 characters")
        }
        if (!avatarUrl.isNullOrEmpty()) validateAvatarUrl(avatarUrl)

        return users.updateProfile(
            id = userId,
            displayName = displayName?.ifEmpty { null },
            clearDisplayName = displayName?.isEmpty() == true,
            avatarUrl = avatarUrl?.ifEmpty { null },
            clearAvatarUrl = avatarUrl?.isEmpty() == true,
        )?.toDto() ?: throw NotFoundException("User no longer exists")
    }

    private fun validateUsername(username: String) {
        if (!USERNAME_REGEX.matches(username)) {
            throw ValidationException("Username must be 3–30 characters: letters, numbers, '.' or '_'")
        }
    }

    private fun validateEmail(email: String) {
        if (email.length > 255 || !EMAIL_REGEX.matches(email)) throw ValidationException("Invalid email address")
    }

    private fun validatePassword(password: String) {
        // BCrypt only uses the first 72 bytes, so longer passwords would silently be truncated.
        if (password.length < 8) throw ValidationException("Password must be at least 8 characters")
        if (password.toByteArray().size > 72) throw ValidationException("Password is too long (max 72 bytes)")
    }

    private fun validateAvatarUrl(url: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        if (url.length > 500 || uri?.scheme != "https" || uri.host.isNullOrEmpty()) {
            throw ValidationException("Avatar URL must be an https:// link")
        }
    }

    private fun usernameTaken() = ConflictException("USERNAME_TAKEN", "That username is already taken")
    private fun emailTaken() = ConflictException("EMAIL_TAKEN", "An account with that email already exists")

    private companion object {
        val USERNAME_REGEX = Regex("^[A-Za-z0-9._]{3,30}$")
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

fun UserRecord.toDto() = CurrentUserDto(
    id = id.toString(),
    username = username,
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    createdAt = createdAt.toInstant().toString(),
)
