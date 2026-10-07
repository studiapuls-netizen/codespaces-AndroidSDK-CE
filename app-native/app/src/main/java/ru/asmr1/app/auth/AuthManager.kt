package ru.asmr1.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "asmr1_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuth(
        token: String,
        username: String,
        displayName: String,
        email: String,
        userId: Int
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_EMAIL, email)
            .putInt(KEY_USER_ID, userId)
            .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)
    fun getLoginTime(): Long = prefs.getLong(KEY_LOGIN_TIME, 0L)

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USERNAME = "jwt_username"
        private const val KEY_DISPLAY_NAME = "jwt_display_name"
        private const val KEY_EMAIL = "jwt_email"
        private const val KEY_USER_ID = "jwt_user_id"
        private const val KEY_LOGIN_TIME = "jwt_login_time"
    }
}
