package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

data class FeedResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("tab") val tab: String,
    @SerializedName("page") val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("videos") val videos: List<Video>
)
