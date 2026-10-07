package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

data class SubscribedAuthor(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("avatar") val avatar: String,
    @SerializedName("has_new") val hasNew: Boolean,
    @SerializedName("last_video_date") val lastVideoDate: String?
)

data class SubscribedAuthorsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("authors") val authors: List<SubscribedAuthor>
)

data class SubscriptionsFeedResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("author_id") val authorId: Int,
    @SerializedName("page") val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("videos") val videos: List<Video>
)

data class MarkViewedResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("author_id") val authorId: Int,
    @SerializedName("viewed_at") val viewedAt: Long
)
