package com.example.movielist.backend.features.users

import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
    val avatarEmoji: String?,
    val avatarColor: String?,
    val showInMatches: Boolean,
    val privateLikes: Boolean,
    val friendRequestsFrom: FriendRequestsFrom,
    val messagesFrom: MessagesFrom,
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

    /** Sets whatever [change] sets, then returns the user as they are now. */
    suspend fun update(id: UUID, change: (UpdateStatement) -> Unit): UserRecord? = db.query {
        Users.update({ Users.id eq id }) { change(it) }
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserRecord()
    }

    /**
     * Removes the account; the database removes everything that belongs to it with it. [first]
     * runs in the same transaction, so either all of it happens or none.
     */
    suspend fun <T> delete(id: UUID, first: JdbcTransaction.() -> T): T = db.query {
        val result = first()
        Users.deleteWhere { Users.id eq id }
        result
    }
}

private fun ResultRow.toUserRecord() = UserRecord(
    id = this[Users.id],
    username = this[Users.username],
    email = this[Users.email],
    passwordHash = this[Users.passwordHash],
    displayName = this[Users.displayName],
    avatarUrl = this[Users.avatarUrl],
    avatarEmoji = this[Users.avatarEmoji],
    avatarColor = this[Users.avatarColor],
    showInMatches = this[Users.showInMatches],
    privateLikes = this[Users.privateLikes],
    friendRequestsFrom = FriendRequestsFrom.valueOf(this[Users.friendRequestsFrom]),
    messagesFrom = MessagesFrom.valueOf(this[Users.messagesFrom]),
    createdAt = this[Users.createdAt],
)
