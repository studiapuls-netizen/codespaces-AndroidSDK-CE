package ru.asmr1.app.data

import com.google.gson.annotations.SerializedName

/**
 * Модели для экрана плейлиста (Фаза 7).
 *
 * Соответствует ответу GET /yt/v1/playlist/{id}
 */
data class PlaylistFullResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("playlist") val playlist: PlaylistInfo,
    @SerializedName("videos")   val videos: List<ProfileVideo>
)

data class PlaylistInfo(
    @SerializedName("id")         val id: Int,
    @SerializedName("title")      val title: String,
    @SerializedName("type")       val type: String,   // "watchlater" | "liked" | "custom"
    @SerializedName("count")      val count: Int,
    @SerializedName("can_edit")   val canEdit: Boolean,
    @SerializedName("visibility") val visibility: String? = null
)

/** Тело запроса POST /yt/v1/playlist/{id}/reorder */
data class ReorderRequest(
    @SerializedName("order") val order: List<Int>
)

data class ReorderResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("order")   val order: List<Int>?
)

data class RemoveFromPlaylistResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("video_id") val videoId: Int?
)

/** POST /yt/v1/playlist/create */
data class CreatePlaylistRequest(
    @SerializedName("title")      val title: String,
    @SerializedName("visibility") val visibility: String // "public" | "private"
)

data class CreatePlaylistResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("playlist") val playlist: PlaylistInfo?,
    @SerializedName("code")     val code: String? = null,
    @SerializedName("message")  val message: String? = null
)

/** POST /yt/v1/playlist/{id}/add/{video_id} */
data class AddToPlaylistResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("playlist_id") val playlistId: Int? = null,
    @SerializedName("video_id") val videoId: Int? = null,
    @SerializedName("already_in") val alreadyIn: Boolean = false,
    @SerializedName("code")     val code: String? = null,
    @SerializedName("message")  val message: String? = null
)

/** GET /yt/v1/video/{id}/playlists */
data class VideoInPlaylistsResponse(
    @SerializedName("success")      val success: Boolean,
    @SerializedName("video_id")     val videoId: Int,
    @SerializedName("playlist_ids") val playlistIds: List<Int> = emptyList()
)

/** POST /yt/v1/playlist/{id}/update */
data class UpdatePlaylistRequest(
    @SerializedName("title")      val title: String,
    @SerializedName("visibility") val visibility: String
)

data class UpdatePlaylistResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("playlist") val playlist: UpdatedPlaylistInfo? = null,
    @SerializedName("code")     val code: String? = null,
    @SerializedName("message")  val message: String? = null
)

data class UpdatedPlaylistInfo(
    @SerializedName("id")         val id: Int,
    @SerializedName("title")      val title: String,
    @SerializedName("visibility") val visibility: String
)

/** POST /yt/v1/playlist/{id}/delete */
data class DeletePlaylistResponse(
    @SerializedName("success")     val success: Boolean,
    @SerializedName("playlist_id") val playlistId: Int? = null,
    @SerializedName("code")        val code: String? = null,
    @SerializedName("message")     val message: String? = null
)
