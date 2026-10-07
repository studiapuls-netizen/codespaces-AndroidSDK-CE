package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

data class SearchResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("query") val query: String,
    @SerializedName("total") val total: Int,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("videos") val videos: List<Video>
)
