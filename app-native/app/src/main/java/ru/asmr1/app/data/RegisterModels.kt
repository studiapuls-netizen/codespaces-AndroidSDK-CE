package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

/**
 * Ответ GET /yt/v1/register/captcha
 */
data class CaptchaResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("captcha_id") val captchaId: String,
    @SerializedName("question") val question: String,
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null
)

/**
 * Запрос POST /yt/v1/register
 */
data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("captcha_id") val captchaId: String,
    @SerializedName("captcha_answer") val captchaAnswer: String
)

/**
 * Ответ POST /yt/v1/register
 */
data class RegisterResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("user_id") val userId: Int?,
    @SerializedName("user_email") val userEmail: String?,
    @SerializedName("user_nicename") val userNicename: String?,
    @SerializedName("user_display_name") val userDisplayName: String?,
    @SerializedName("code") val code: String? = null
)
