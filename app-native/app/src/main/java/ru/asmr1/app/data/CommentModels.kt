package ru.asmr1.app.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Один комментарий.
 */
@Parcelize
data class Comment(
    @SerializedName("id")            val id: Int,
    @SerializedName("content")       val content: String,
    @SerializedName("author_id")     val authorId: Int,
    @SerializedName("author_name")   val authorName: String,
    @SerializedName("author_avatar") val authorAvatar: String,
    @SerializedName("date")          val date: String,
    @SerializedName("date_gmt")      val dateGmt: String,
    @SerializedName("likes")         val likes: Int,
    @SerializedName("liked")         val liked: Boolean,
    @SerializedName("parent_id")     val parentId: Int,
    @SerializedName("replies_count") val repliesCount: Int,
    @SerializedName("replies")       val replies: List<Comment> = emptyList()
) : Parcelable

/**
 * Ответ GET /yt/v1/comments/{video_id}
 */
data class CommentsResponse(
    @SerializedName("success")  val success: Boolean,
    @SerializedName("video_id") val videoId: Int,
    @SerializedName("total")    val total: Int,
    @SerializedName("page")     val page: Int,
    @SerializedName("per_page") val perPage: Int,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("comments") val comments: List<Comment>
)

/**
 * Тело запроса POST /yt/v1/comment/{video_id}
 */
data class CommentCreateRequest(
    @SerializedName("content")   val content: String,
    @SerializedName("parent_id") val parentId: Int = 0
)

/**
 * Ответ POST /yt/v1/comment/{video_id}
 */
data class CommentCreateResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("comment") val comment: Comment?,
    @SerializedName("code")    val code: String? = null,
    @SerializedName("message") val message: String? = null
)

/**
 * Ответ POST /yt/v1/comment-like/{comment_id}
 */
data class CommentLikeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("id")      val id: Int,
    @SerializedName("likes")   val likes: Int,
    @SerializedName("liked")   val liked: Boolean,
    @SerializedName("action")  val action: String?,
    @SerializedName("code")    val code: String? = null,
    @SerializedName("message") val message: String? = null
)
