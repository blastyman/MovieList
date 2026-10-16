package com.example.movielist.backend

import com.example.movielist.backend.features.health.HealthResponse
import com.example.movielist.backend.plugins.AppJson
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    @Test
    fun `health reports database up`() = testApplication {
        application { module(TestDatabase.config, TestDatabase.dataSource) }

        val response = jsonClient().get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse(status = "ok", database = "up"), response.body())
    }

    @Test
    fun `unknown route returns error json`() = testApplication {
        application { module(TestDatabase.config, TestDatabase.dataSource) }

        val response = jsonClient().get("/does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", response.body<ErrorResponse>().error)
    }
}
