package com.example.movielist.backend.util

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Small in-memory cache with a time-to-live per entry. Used for TMDB list responses, which
 * are the same for every user and change slowly. Lost on restart, which is fine for this data.
 */
class TtlCache<K : Any, V : Any>(
    private val ttl: Duration,
    private val maxEntries: Int = 1_000,
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Entry<V>(val value: V, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<K, Entry<V>>()

    suspend fun getOrPut(key: K, load: suspend () -> V): V {
        val now = clock.instant()
        entries[key]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.value }

        val value = load()
        if (entries.size >= maxEntries) evictExpired(now)
        if (entries.size < maxEntries) entries[key] = Entry(value, now.plus(ttl))
        return value
    }

    private fun evictExpired(now: Instant) {
        entries.entries.removeIf { !it.value.expiresAt.isAfter(now) }
    }
}
