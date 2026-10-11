package ru.asmr1.app.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ProfileVideo(
    @SerializedName("id")          val id: Int,
    @SerializedName("title")       val title: String,
    @SerializedName("thumbnail")   val thumbnail: String,
    @SerializedName("duration")    val duration: Int,
    @SerializedName("views")       val views: Int,
    @SerializedName("video_url")   val videoUrl: String?,
    @SerializedName("audio_url")   val audioUrl: String?,
    @SerializedName("author_id")   val authorId: Int,
    @SerializedName("author_name") val authorName: String
) : Parcelable

@Parcelize
data class ProfileUser(
    @SerializedName("id")     val id: Int,
    @SerializedName("name")   val name: String,
    @SerializedName("email")  val email: String,
    @SerializedName("avatar") val avatar: String
) : Parcelable

data class PlaylistSummary(
    @SerializedName("id")          val playlistId: Int = 0,
    @SerializedName("count")       val count: Int,
    @SerializedName("first_video") val firstVideo: ProfileVideo?
)

data class PlaylistCustom(
    @SerializedName("id")          val id: Int,
    @SerializedName("title")       val title: String,
    @SerializedName("type")        val type: String = "custom",
    @SerializedName("count")       val count: Int = 0,
    @SerializedName("can_edit")    val canEdit: Boolean = true,
    @SerializedName("visibility")  val visibility: String = "public",
    @SerializedName("first_video") val firstVideo: ProfileVideo? = null
)

data class MeProfileResponse(
    @SerializedName("success")          val success: Boolean,
    @SerializedName("user")             val user: ProfileUser,
    @SerializedName("history")          val history: List<ProfileVideo>,
    @SerializedName("watchlater")       val watchlater: PlaylistSummary,
    @SerializedName("liked")            val liked: PlaylistSummary,
    @SerializedName("custom_playlists") val customPlaylists: List<PlaylistCustom> = emptyList()
)

data class MeHistoryResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("page")     val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total")    val total: Int,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("items")    val items: List<ProfileVideo>
)
/** POST /yt/v1/me/history/remove/{id} */
data class HistoryRemoveResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("video_id") val videoId: Int? = null,
    @SerializedName("total")    val total: Int = 0,
    @SerializedName("code")     val code: String? = null,
    @SerializedName("message")  val message: String? = null
)

/** POST /yt/v1/me/history/clear */
data class HistoryClearResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("total")   val total: Int = 0,
    @SerializedName("code")    val code: String? = null,
    @SerializedName("message") val message: String? = null
)
