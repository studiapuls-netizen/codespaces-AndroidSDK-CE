package ru.asmr1.app.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Video(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("video_url") val videoUrl: String,
    @SerializedName("audio_url") val audioUrl: String?,
    @SerializedName("duration") val duration: Int,
    @SerializedName("views") val views: Int,
    @SerializedName("likes") val likes: Int,
    @SerializedName("published") val published: String,
    @SerializedName("author") val author: Author,
    @SerializedName("tags") val tags: List<String>?
) : Parcelable

@Parcelize
data class Author(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("avatar") val avatar: String
) : Parcelable
