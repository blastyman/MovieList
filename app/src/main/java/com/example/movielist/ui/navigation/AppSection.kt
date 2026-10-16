package com.example.movielist.ui.navigation

/** The drawer's top-level destinations. Each one is a Navigation Compose route. */
enum class AppSection(val displayName: String) {
    HOME("home"),
    SEARCH("search"),
    FRIENDS("friends"),
    CHATS("chats"),
    GROUPS("groups"),
    LISTS("lists"),
    MATCHES("matches"),
    USER("user");

    val route: String
        get() = name.lowercase()

    companion object {
        /** The section a route belongs to; a group's screens count as groups, a chat as chats. */
        fun fromRoute(route: String?): AppSection? =
            entries.firstOrNull { it.route == route }
                ?: GROUPS.takeIf { route?.startsWith("group") == true }
                ?: CHATS.takeIf { route?.startsWith("chat") == true }
    }
}
