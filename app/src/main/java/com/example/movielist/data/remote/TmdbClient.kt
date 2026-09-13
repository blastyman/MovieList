package com.example.movielist.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TmdbClient {
    private const val BASE_URL = "https://api.themoviedb.org/3/"

    fun createApi(token: String): TmdbApi {
        val authorization = if (token.startsWith("Bearer ")) token else "Bearer $token"
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder().header("Authorization", authorization).build()
                    chain.proceed(request)
                }
                .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }
}
