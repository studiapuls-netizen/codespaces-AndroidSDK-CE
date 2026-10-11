package ru.asmr1.app.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationItem(
    @SerializedName("id")                  val id: String,
    @SerializedName("index")               val index: Int,
    @SerializedName("type")                val type: String,
    @SerializedName("post_id")             val postId: Int,
    @SerializedName("video_url")           val videoUrl: String?,
    @SerializedName("audio_url")           val audioUrl: String?,
    @SerializedName("title")               val title: String,
    @SerializedName("thumbnail")           val thumbnail: String,
    @SerializedName("author_id")           val authorId: Int,
    @SerializedName("author_name")         val authorName: String,
    @SerializedName("author_avatar")       val authorAvatar: String,
    @SerializedName("comment_id")          val commentId: Int,
    @SerializedName("parent_comment_id")   val parentCommentId: Int,
    @SerializedName("comment_text")        val commentText: String,
    @SerializedName("read")                val read: Boolean,
    @SerializedName("timestamp")           val timestamp: String
) : Parcelable

data class NotificationsResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("filter")   val filter: String,
    @SerializedName("page")     val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("total")    val total: Int,
    @SerializedName("unread")   val unread: Int,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("items")    val items: List<NotificationItem>
)

data class NotificationReadResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("id")      val id: String? = null,
    @SerializedName("message") val message: String? = null
)

data class NotificationUnreadResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("unread")  val unread: Int = 0
)
