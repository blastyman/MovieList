package com.example.movielist.ui.navigation

/** The drawer's top-level destinations. Each one is a Navigation Compose route. */
enum class AppSection(val displayName: String) {
    HOME("home"),
    SEARCH("search"),
    FRIENDS("friends"),
    LISTS("lists"),
    MATCHES("matches"),
    USER("user");

    val route: String
        get() = name.lowercase()

    companion object {
        fun fromRoute(route: String?): AppSection? = entries.firstOrNull { it.route == route }
    }
}
