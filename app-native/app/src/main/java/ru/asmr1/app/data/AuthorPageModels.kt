package ru.asmr1.app.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class AuthorPageResponse(
    @SerializedName("id")                val id: Int,
    @SerializedName("nickname")          val nickname: String,
    @SerializedName("avatar")            val avatar: String,
    @SerializedName("banner")            val banner: String?,
    @SerializedName("description")       val description: String?,
    @SerializedName("socials")           val socials: Socials,
    @SerializedName("subscribers_count") val subscribersCount: Int,
    @SerializedName("video_count")       val videoCount: Int,
    @SerializedName("total_views")       val totalViews: Int,
    @SerializedName("channel_joined")    val channelJoined: String?,
    @SerializedName("is_author")         val isAuthor: Boolean,
    @SerializedName("follow_status")     val followStatus: Boolean,
    @SerializedName("tabs")              val tabs: AuthorTabs?
) : Parcelable

@Parcelize
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
) : Parcelable

@Parcelize
data class AuthorTabs(
    @SerializedName("popular")           val popular: List<Video>,
    @SerializedName("videos")            val videos: List<Video>,
    @SerializedName("playlists")         val playlists: List<AuthorPlaylist>,
    @SerializedName("videos_pagination") val videosPagination: AuthorVideosPagination?
) : Parcelable

@Parcelize
data class AuthorVideosPagination(
    @SerializedName("page")     val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total")    val total: Int,
    @SerializedName("has_more") val hasMore: Boolean
) : Parcelable

@Parcelize
data class AuthorPlaylist(
    @SerializedName("id")         val id: Int,
    @SerializedName("title")      val title: String,
    @SerializedName("count")      val count: Int,
    @SerializedName("thumbnail")  val thumbnail: String?,
    @SerializedName("type")       val type: String,
    @SerializedName("visibility") val visibility: String?
) : Parcelable

// ============================================================
// Кастомный десериализатор для Socials: переваривает и {} и []
// ============================================================
object SocialsDeserializer : com.google.gson.JsonDeserializer<Socials> {
    override fun deserialize(
        json: com.google.gson.JsonElement?,
        typeOfT: java.lang.reflect.Type?,
        context: com.google.gson.JsonDeserializationContext?
    ): Socials {
        // Если сервер вернул пустой массив [] или null — отдаём пустой Socials
        if (json == null || json.isJsonNull || json.isJsonArray) {
            return Socials(null, null, null, null, null, null, null, null, null)
        }
        // Если объект — берём поля как обычно
        val obj = json.asJsonObject
        fun s(key: String) = obj.get(key)?.takeIf { !it.isJsonNull }?.asString
        return Socials(
            youtube        = s("youtube"),
            telegram       = s("telegram"),
            vk             = s("vk"),
            rutube         = s("rutube"),
            dzen           = s("dzen"),
            boosty         = s("boosty"),
            patreon        = s("patreon"),
            donationalerts = s("donationalerts"),
            website        = s("website")
        )
    }
}
