package com.example.movielist.domain.model

/**
 * How someone looks in a circle: their photo if they uploaded one, else their emoji on their
 * colour, else the first letter of their name.
 */
data class Avatar(
    /** Full address of the photo. */
    val photoUrl: String? = null,
    val emoji: String? = null,
    /** `#RRGGBB`. */
    val color: String? = null,
) {
    companion object {
        val NONE = Avatar()
    }
}

/** Who may send you a friend request. */
enum class FriendRequestsFrom {
    EVERYONE,
    /** Only people who like at least one of the same movies. */
    MATCHES,
    NOBODY,
}

/** Who may chat with you one-to-one; group chats aren't affected. */
enum class MessagesFrom { FRIENDS, NOBODY }

data class Privacy(
    /** Strangers see you in their matches; friends always do. */
    val showInMatches: Boolean = true,
    /** Only friends see your liked movies. */
    val privateLikes: Boolean = false,
    val friendRequestsFrom: FriendRequestsFrom = FriendRequestsFrom.EVERYONE,
    val messagesFrom: MessagesFrom = MessagesFrom.FRIENDS,
)
