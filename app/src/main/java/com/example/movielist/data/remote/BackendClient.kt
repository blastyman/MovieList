package com.example.movielist.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object BackendClient {
    /**
     * @param tokenProvider returns the current JWT, or null when logged out.
     * @param onUnauthorized called when the server rejects a token we sent (expired, revoked,
     *   forged), so the app can end the session.
     */
    fun createApi(
        baseUrl: String,
        tokenProvider: () -> String?,
        onUnauthorized: () -> Unit,
    ): BackendApi {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val token = tokenProvider()
                    val request =
                        if (token == null) chain.request()
                        else
                            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                    val response = chain.proceed(request)
                    val isAuthEndpoint = request.url.encodedPath.startsWith("/auth/")
                    if (response.code == 401 && token != null && !isAuthEndpoint) onUnauthorized()
                    response
                }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }
}
