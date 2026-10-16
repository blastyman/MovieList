package com.example.movielist.backend.features.users

import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val displayName: String?,
    val avatarUrl: String?,
    val createdAt: OffsetDateTime,
)

class UserRepository(private val db: Database) {

    suspend fun create(username: String, email: String, passwordHash: String): UserRecord = db.query {
        val id = Users.insert {
            it[Users.username] = username
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
        }[Users.id]
        Users.selectAll().where { Users.id eq id }.single().toUserRecord()
    }

    suspend fun findById(id: UUID): UserRecord? = db.query {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserRecord()
    }

    /** Case-insensitive match on username or email. */
    suspend fun findByLogin(login: String): UserRecord? = db.query {
        val normalized = login.lowercase()
        Users.selectAll()
            .where { (Users.username.lowerCase() eq normalized) or (Users.email.lowerCase() eq normalized) }
            .singleOrNull()
            ?.toUserRecord()
    }

    suspend fun existsByUsername(username: String): Boolean = db.query {
        !Users.selectAll().where { Users.username.lowerCase() eq username.lowercase() }.empty()
    }

    suspend fun existsByEmail(email: String): Boolean = db.query {
        !Users.selectAll().where { Users.email.lowerCase() eq email.lowercase() }.empty()
    }

    /** Pass a value to change it; `clear*` sets the column back to null. */
    suspend fun updateProfile(
        id: UUID,
        displayName: String?,
        clearDisplayName: Boolean,
        avatarUrl: String?,
        clearAvatarUrl: Boolean,
    ): UserRecord? = db.query {
        if (displayName != null || clearDisplayName || avatarUrl != null || clearAvatarUrl) {
            Users.update({ Users.id eq id }) {
                if (clearDisplayName) it[Users.displayName] = null else if (displayName != null) it[Users.displayName] = displayName
                if (clearAvatarUrl) it[Users.avatarUrl] = null else if (avatarUrl != null) it[Users.avatarUrl] = avatarUrl
            }
        }
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserRecord()
    }
}

private fun ResultRow.toUserRecord() = UserRecord(
    id = this[Users.id],
    username = this[Users.username],
    email = this[Users.email],
    passwordHash = this[Users.passwordHash],
    displayName = this[Users.displayName],
    avatarUrl = this[Users.avatarUrl],
    createdAt = this[Users.createdAt],
)
