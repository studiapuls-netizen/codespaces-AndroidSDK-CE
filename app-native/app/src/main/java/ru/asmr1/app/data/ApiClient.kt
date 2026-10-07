package ru.asmr1.app.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.asmr1.app.auth.AuthInterceptor
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://asmr1.ru/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL + "wp-json/yt/v1/")
            .client(baseClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val authApi: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(baseClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    fun createJwtService(token: String): ApiService {
        val jwtClient = baseClient.newBuilder()
            .addInterceptor(AuthInterceptor(token))
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL + "wp-json/yt/v1/")
            .client(jwtClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun createJwtAuthService(token: String): AuthApiService {
        val jwtClient = baseClient.newBuilder()
            .addInterceptor(AuthInterceptor(token))
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(jwtClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}
