package com.example.movielist.backend.db

import com.example.movielist.backend.features.movies.MovieDetailsDto
import com.example.movielist.backend.plugins.AppJson
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/*
 * Exposed mappings for the schema in resources/db/migration.
 * Flyway owns the schema; these objects only describe it for type-safe queries,
 * so any change here must come with a new migration (V2__…, V3__…).
 */

object Users : Table("users") {
    val id = javaUUID("id").autoGenerate()
    val username = varchar("username", 30)
    val email = varchar("email", 255)
    val passwordHash = text("password_hash")
    val displayName = varchar("display_name", 50).nullable()
    val avatarUrl = text("avatar_url").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object Friendships : Table("friendships") {
    val requesterId = javaUUID("requester_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val addresseeId = javaUUID("addressee_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 10) // FriendshipStatus
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val respondedAt = timestampWithTimeZone("responded_at").nullable()

    override val primaryKey = PrimaryKey(requesterId, addresseeId)
}

object FriendGroups : Table("friend_groups") {
    val id = javaUUID("id").autoGenerate()
    val name = varchar("name", 50)
    val ownerId = javaUUID("owner_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object GroupMembers : Table("group_members") {
    val groupId = javaUUID("group_id").references(FriendGroups.id, onDelete = ReferenceOption.CASCADE)
    val userId = javaUUID("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val joinedAt = timestampWithTimeZone("joined_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(groupId, userId)
}

object Conversations : Table("conversations") {
    val id = javaUUID("id").autoGenerate()
    val type = varchar("type", 10) // ConversationType
    val groupId = javaUUID("group_id").references(FriendGroups.id, onDelete = ReferenceOption.CASCADE).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object ConversationMembers : Table("conversation_members") {
    val conversationId = javaUUID("conversation_id").references(Conversations.id, onDelete = ReferenceOption.CASCADE)
    val userId = javaUUID("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val lastReadAt = timestampWithTimeZone("last_read_at").nullable()

    override val primaryKey = PrimaryKey(conversationId, userId)
}

object Messages : Table("messages") {
    val id = javaUUID("id").autoGenerate()
    val conversationId = javaUUID("conversation_id").references(Conversations.id, onDelete = ReferenceOption.CASCADE)
    val senderId = javaUUID("sender_id").references(Users.id)
    val body = text("body")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}

object MovieReactions : Table("movie_reactions") {
    val userId = javaUUID("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val movieId = integer("movie_id")
    val reaction = varchar("reaction", 8) // Reaction
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(userId, movieId)
}

object Ratings : Table("ratings") {
    val userId = javaUUID("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val movieId = integer("movie_id")
    val score = short("score")
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(userId, movieId)
}

object Comments : Table("comments") {
    val id = javaUUID("id").autoGenerate()
    val movieId = integer("movie_id")
    val userId = javaUUID("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val body = text("body")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val editedAt = timestampWithTimeZone("edited_at").nullable()
    val deleted = bool("deleted").default(false)

    override val primaryKey = PrimaryKey(id)
}

/** TMDB movie details, refreshed after a TTL (see MovieService). */
object MoviesCache : Table("movies_cache") {
    val movieId = integer("movie_id")
    val json = jsonb<MovieDetailsDto>("json", AppJson)
    val fetchedAt = timestampWithTimeZone("fetched_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(movieId)
}

enum class FriendshipStatus { PENDING, ACCEPTED }
enum class ConversationType { DIRECT, GROUP }
enum class Reaction { LIKE, DISLIKE }

/** Business rule from the thesis spec: a friend group holds at most 10 people, owner included. */
const val MAX_GROUP_MEMBERS = 10
