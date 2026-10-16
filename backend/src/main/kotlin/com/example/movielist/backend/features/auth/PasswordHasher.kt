package com.example.movielist.backend.features.auth

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordHasher(private val cost: Int = 12) {

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    /**
     * Used when a login names a user that doesn't exist, so the response takes as long as a
     * wrong password would. Otherwise response timing reveals which usernames are registered.
     */
    val dummyHash: String by lazy { hash("timing-equaliser-not-a-real-password") }
}
