package ru.asmr1.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {

    @POST("wp-json/jwt-auth/v1/token")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("wp-json/jwt-auth/v1/token/validate")
    suspend fun validateToken(): ValidateResponse

    @GET("wp-json/wp/v2/users/me")
    suspend fun getCurrentUser(): User

    // ============================================================
    // РЕГИСТРАЦИЯ (новое)
    // ============================================================

    @GET("wp-json/yt/v1/register/captcha")
    suspend fun getRegisterCaptcha(): CaptchaResponse

    @POST("wp-json/yt/v1/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse
}
