package com.example.movielist.data.remote

import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.GzipSource
import okio.buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private val client = OkHttpClient.Builder()

        .addInterceptor { chain ->

            val request = chain.request()

            val response = chain.proceed(request)

            val body = response.body

            val encoding = response.header("Content-Encoding")

            if (encoding.equals("gzip", ignoreCase = true) && body != null) {

                val source = GzipSource(body.source())
                val decompressed = source.buffer().readUtf8()

                val newBody = decompressed.toByteArray()
                    .toResponseBody("application/json".toMediaType())

                response.newBuilder()
                    .removeHeader("Content-Encoding")
                    .body(newBody)
                    .build()

            } else {
                response
            }
        }

        .retryOnConnectionFailure(true)
        .build()

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    val api: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(TmdbApi::class.java)
}