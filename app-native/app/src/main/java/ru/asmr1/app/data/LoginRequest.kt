package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)

data class TokenResponse(
    @SerializedName("token") val token: String,
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("user_nicename") val userNicename: String,
    @SerializedName("user_display_name") val userDisplayName: String
)

data class ValidateResponse(
    val code: String,
    val data: ValidateData?
)

data class ValidateData(
    val status: Int
)
