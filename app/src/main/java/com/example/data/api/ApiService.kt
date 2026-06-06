package com.example.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

interface MyMemoryApi {
    @GET("api/get")
    suspend fun getTranslation(
        @Query("q") query: String,
        @Query("langpair") langPair: String = "en|fa"
    ): MyMemoryResponseDto
}

interface DictionaryApi {
    @GET("api/v2/entries/en/{word}")
    suspend fun getDefinitions(
        @Path("word") word: String
    ): List<DictionaryEntry>
}

interface FreeDictionaryApi {
    @GET("api/v1/entries/en/{word}")
    suspend fun getDefinitions(
        @Path("word") word: String
    ): FreeDictResponse
}

object RetrofitClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    val myMemoryApi: MyMemoryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mymemory.translated.net/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MyMemoryApi::class.java)
    }

    val dictionaryApi: DictionaryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.dictionaryapi.dev/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DictionaryApi::class.java)
    }

    val freeDictionaryApi: FreeDictionaryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://freedictionaryapi.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FreeDictionaryApi::class.java)
    }
}
