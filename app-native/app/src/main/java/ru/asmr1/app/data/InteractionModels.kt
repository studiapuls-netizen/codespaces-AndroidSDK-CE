package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

// ============================================================
// ЛАЙК
// ============================================================

data class LikeStatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("id") val id: Int,
    @SerializedName("likes") val likes: Int,
    @SerializedName("dislikes") val dislikes: Int,
    @SerializedName("current_vote") val currentVote: String?, // "like" | "dislike" | null
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null
)

data class LikeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("id") val id: Int,
    @SerializedName("likes") val likes: Int,
    @SerializedName("dislikes") val dislikes: Int,
    @SerializedName("current_vote") val currentVote: String?,
    @SerializedName("action") val action: String?, // "liked" | "unliked"
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null
)

// ============================================================
// ПОДПИСКА
// ============================================================

data class SubscriptionStatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("author_id") val authorId: Int,
    @SerializedName("subscribed") val subscribed: Boolean,
    @SerializedName("subscribers_count") val subscribersCount: Int,
    @SerializedName("notifications_enabled") val notificationsEnabled: Boolean,
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null
)

data class SubscribeRequest(
    @SerializedName("action") val action: String // "sub" | "unsub" | "notify" | "mute"
)

data class SubscribeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("author_id") val authorId: Int,
    @SerializedName("action") val action: String?,
    @SerializedName("subscribed") val subscribed: Boolean,
    @SerializedName("subscribers_count") val subscribersCount: Int,
    @SerializedName("notifications_enabled") val notificationsEnabled: Boolean,
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null
)
