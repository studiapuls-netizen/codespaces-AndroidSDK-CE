package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    val name: String,
    val description: String?,
    val slug: String?,
    @SerializedName("avatar_urls") val avatarUrls: Map<String, String>?
) {
    val avatarUrl: String?
        get() = avatarUrls?.get("96") ?: avatarUrls?.get("48") ?: avatarUrls?.get("24")
}
