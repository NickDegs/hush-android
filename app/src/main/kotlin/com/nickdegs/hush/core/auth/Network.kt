package com.nickdegs.hush.core.auth

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object Network {
    private const val BASE_URL = "https://chat.nickdegs.com/"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val ok: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(com.nickdegs.hush.core.net.HushNet.signatureInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val phoneAuth: PhoneAuthApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(ok)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PhoneAuthApi::class.java)

    // Spaces: platform="android" gibi default değerlerin de gönderilmesi gerekir (encodeDefaults=true).
    private val spacesJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val spaces: com.nickdegs.hush.core.spaces.SpacesApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(ok)
        .addConverterFactory(spacesJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(com.nickdegs.hush.core.spaces.SpacesApi::class.java)
}
