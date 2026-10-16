package com.example.movielist.backend.features.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

class JwtService(secret: String, private val validity: Duration = Duration.ofDays(7)) {

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun issueToken(userId: UUID): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId.toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(validity)))
            .sign(algorithm)
    }

    companion object {
        const val ISSUER = "movielist-backend"
        const val AUDIENCE = "movielist-app"
        const val REALM = "movielist"
    }
}
