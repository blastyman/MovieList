package com.example.movielist.backend.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.postgresql.util.PSQLException

/** Runs a blocking JDBC transaction on the IO dispatcher so route handlers don't block Netty threads. */
suspend fun <T> Database.query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@query) { block() } }

/** Name of the unique constraint/index a failed INSERT/UPDATE hit, or null if it wasn't a unique violation. */
fun ExposedSQLException.violatedUniqueConstraint(): String? {
    if (sqlState != "23505") return null
    return (cause as? PSQLException)?.serverErrorMessage?.constraint
}
