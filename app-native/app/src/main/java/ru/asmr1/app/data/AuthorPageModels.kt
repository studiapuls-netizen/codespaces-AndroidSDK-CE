package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

data class AuthorPageResponse(
    @SerializedName("id")                val id: Int,
    @SerializedName("nickname")          val nickname: String,
    @SerializedName("avatar")            val avatar: String,
    @SerializedName("banner")            val banner: String?,
    @SerializedName("description")       val description: String?,
    @SerializedName("socials")           val socials: Socials?,
    @SerializedName("subscribers_count") val subscribersCount: Int,
    @SerializedName("video_count")       val videoCount: Int,
    @SerializedName("total_views")       val totalViews: Int,
    @SerializedName("channel_joined")    val channelJoined: String?,
    @SerializedName("is_author")         val isAuthor: Boolean,
    @SerializedName("follow_status")     val followStatus: Boolean,
    @SerializedName("tabs")              val tabs: AuthorTabs?
)

data class Socials(
    @SerializedName("youtube")         val youtube: String?,
    @SerializedName("telegram")        val telegram: String?,
    @SerializedName("vk")              val vk: String?,
    @SerializedName("rutube")          val rutube: String?,
    @SerializedName("dzen")            val dzen: String?,
    @SerializedName("boosty")          val boosty: String?,
    @SerializedName("patreon")         val patreon: String?,
    @SerializedName("donationalerts")  val donationalerts: String?,
    @SerializedName("website")         val website: String?
)

data class AuthorTabs(
    @SerializedName("popular")           val popular: List<Video>,
    @SerializedName("videos")            val videos: List<Video>,
    @SerializedName("playlists")         val playlists: List<AuthorPlaylist>,
    @SerializedName("videos_pagination") val videosPagination: AuthorVideosPagination?
)

data class AuthorVideosPagination(
    @SerializedName("page")     val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total")    val total: Int,
    @SerializedName("has_more") val hasMore: Boolean
)

data class AuthorPlaylist(
    @SerializedName("id")         val id: Int,
    @SerializedName("title")      val title: String,
    @SerializedName("count")      val count: Int,
    @SerializedName("thumbnail")  val thumbnail: String?,
    @SerializedName("type")       val type: String,
    @SerializedName("visibility") val visibility: String?
)
