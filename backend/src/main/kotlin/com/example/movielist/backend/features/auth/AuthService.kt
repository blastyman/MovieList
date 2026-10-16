package com.example.movielist.backend.features.auth

import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.violatedUniqueConstraint
import com.example.movielist.backend.features.groups.GroupService
import com.example.movielist.backend.features.users.AvatarStore
import com.example.movielist.backend.features.users.validAvatarParts
import com.example.movielist.backend.features.users.UserRecord
import com.example.movielist.backend.features.users.UserRepository
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.ConflictException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.util.UUID

class InvalidCredentialsException :
    ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Wrong username/email or password")

class WrongPasswordException :
    ApiException(HttpStatusCode.Forbidden, "WRONG_PASSWORD", "That password isn't right")

class AuthService(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val jwt: JwtService,
    private val groups: GroupService,
    private val avatars: AvatarStore,
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

    /** A blank string clears the field (the username can only be changed); a missing field stays. */
    suspend fun updateMe(userId: UUID, request: UpdateMeRequest): CurrentUserDto {
        val displayName = request.displayName?.trim()
        val username = request.username?.trim()
        val (emoji, color) = validAvatarParts(request.avatarEmoji, request.avatarColor)

        if (!displayName.isNullOrEmpty() && displayName.length > 50) {
            throw ValidationException("Display name must be at most 50 characters")
        }
        if (username != null) validateUsername(username)

        val updated = try {
            users.update(userId) {
                if (displayName != null) it[Users.displayName] = displayName.ifEmpty { null }
                if (username != null) it[Users.username] = username
                if (emoji != null) it[Users.avatarEmoji] = emoji.ifEmpty { null }
                if (color != null) it[Users.avatarColor] = color.ifEmpty { null }
            }
        } catch (e: ExposedSQLException) {
            if (e.violatedUniqueConstraint() == "users_username_lower_key") throw usernameTaken() else throw e
        }
        return updated?.toDto() ?: throw NotFoundException("User no longer exists")
    }

    suspend fun updatePrivacy(userId: UUID, request: UpdatePrivacyRequest): CurrentUserDto =
        users.update(userId) {
            request.showInMatches?.let { value -> it[Users.showInMatches] = value }
            request.privateLikes?.let { value -> it[Users.privateLikes] = value }
            request.friendRequestsFrom?.let { value -> it[Users.friendRequestsFrom] = value.name }
            request.messagesFrom?.let { value -> it[Users.messagesFrom] = value.name }
        }?.toDto() ?: throw NotFoundException("User no longer exists")

    suspend fun changeEmail(userId: UUID, request: ChangeEmailRequest): CurrentUserDto {
        val user = requirePassword(userId, request.password)
        val email = request.email.trim()
        validateEmail(email)
        if (!email.equals(user.email, ignoreCase = true) && users.existsByEmail(email)) throw emailTaken()
        val updated = try {
            users.update(userId) { it[Users.email] = email }
        } catch (e: ExposedSQLException) {
            if (e.violatedUniqueConstraint() == "users_email_lower_key") throw emailTaken() else throw e
        }
        return updated?.toDto() ?: throw NotFoundException("User no longer exists")
    }

    suspend fun changePassword(userId: UUID, request: ChangePasswordRequest) {
        requirePassword(userId, request.currentPassword)
        validatePassword(request.newPassword)
        users.update(userId) { it[Users.passwordHash] = hasher.hash(request.newPassword) }
    }

    /** Saves a new profile picture in place of the old one. */
    suspend fun setAvatar(userId: UUID, bytes: ByteArray): CurrentUserDto {
        val old = users.findById(userId) ?: throw NotFoundException("User no longer exists")
        val path = avatars.save(userId, bytes)
        val updated = users.update(userId) { it[Users.avatarUrl] = path }
        avatars.delete(old.avatarUrl)
        return updated?.toDto() ?: throw NotFoundException("User no longer exists")
    }

    suspend fun removeAvatar(userId: UUID): CurrentUserDto {
        val old = users.findById(userId) ?: throw NotFoundException("User no longer exists")
        val updated = users.update(userId) { it[Users.avatarUrl] = null }
        avatars.delete(old.avatarUrl)
        return updated?.toDto() ?: throw NotFoundException("User no longer exists")
    }

    /**
     * Deletes the account and everything in it, in one transaction. Groups they own are handed
     * to the next member first, the same as leaving, so their friends don't lose the group.
     * Pictures are removed only once that has committed.
     */
    suspend fun deleteAccount(userId: UUID, request: DeleteAccountRequest) {
        val user = requirePassword(userId, request.password)
        val deletedGroupPhotos = users.delete(userId) { groups.leaveAllIn(this, userId) }
        (deletedGroupPhotos + user.avatarUrl).forEach(avatars::delete)
    }

    /** The account, if [password] is right; a wrong one is a 403 so the app doesn't log out. */
    private suspend fun requirePassword(userId: UUID, password: String): UserRecord {
        val user = users.findById(userId) ?: throw NotFoundException("User no longer exists")
        if (!hasher.verify(password, user.passwordHash)) throw WrongPasswordException()
        return user
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
    avatarEmoji = avatarEmoji,
    avatarColor = avatarColor,
    privacy = PrivacyDto(showInMatches, privateLikes, friendRequestsFrom, messagesFrom),
    createdAt = createdAt.toInstant().toString(),
)
