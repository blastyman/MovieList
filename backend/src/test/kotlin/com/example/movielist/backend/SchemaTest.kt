package com.example.movielist.backend

import com.example.movielist.backend.db.Conversations
import com.example.movielist.backend.db.ConversationType
import com.example.movielist.backend.db.Friendships
import com.example.movielist.backend.db.FriendshipStatus
import com.example.movielist.backend.db.Ratings
import com.example.movielist.backend.db.Users
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** Checks that the V1 migration runs and that its constraints enforce the rules in §7 of the plan. */
class SchemaTest {

    private val db = Database.connect(TestDatabase.dataSource)

    private fun createUser(name: String = "user-${UUID.randomUUID().toString().take(8)}"): UUID =
        transaction(db) {
            Users.insert {
                it[username] = name
                it[email] = "$name@example.com"
                it[passwordHash] = "hash"
            }[Users.id]
        }

    @Test
    fun `insert user applies database defaults`() {
        val id = createUser()
        assertNotNull(id)
    }

    @Test
    fun `usernames are unique case-insensitively`() {
        val name = "Nikos${UUID.randomUUID().toString().take(6)}"
        createUser(name)

        assertFailsWith<ExposedSQLException> {
            transaction(db) {
                Users.insert {
                    it[username] = name.lowercase()
                    it[email] = "other-$name@example.com"
                    it[passwordHash] = "hash"
                }
            }
        }
    }

    @Test
    fun `reverse friend request is rejected`() {
        val a = createUser()
        val b = createUser()
        insertFriendship(a, b)

        assertFailsWith<ExposedSQLException> { insertFriendship(b, a) }
    }

    @Test
    fun `cannot befriend yourself`() {
        val a = createUser()
        assertFailsWith<ExposedSQLException> { insertFriendship(a, a) }
    }

    @Test
    fun `rating must be between 1 and 10`() {
        val user = createUser()
        assertFailsWith<ExposedSQLException> {
            transaction(db) {
                Ratings.insert {
                    it[userId] = user
                    it[movieId] = 550
                    it[score] = 11
                }
            }
        }
    }

    @Test
    fun `group conversation requires a group`() {
        assertFailsWith<ExposedSQLException> {
            transaction(db) {
                Conversations.insert { it[type] = ConversationType.GROUP.name }
            }
        }
    }

    private fun insertFriendship(from: UUID, to: UUID) = transaction(db) {
        Friendships.insert {
            it[requesterId] = from
            it[addresseeId] = to
            it[status] = FriendshipStatus.PENDING.name
        }
    }
}
