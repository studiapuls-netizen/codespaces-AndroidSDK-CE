package ru.asmr1.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("feed")
    suspend fun getFeed(
        @Query("tab") tab: String = "recommended",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): FeedResponse

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("orderby") orderBy: String = "weight",
        @Query("order") order: String = "DESC"
    ): SearchResponse

    @GET("subscriptions/authors")
    suspend fun getSubscribedAuthors(): SubscribedAuthorsResponse

    @GET("subscriptions/feed")
    suspend fun getSubscriptionsFeed(
        @Query("author_id") authorId: Int = 0,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): SubscriptionsFeedResponse

    @POST("mark-viewed/{id}")
    suspend fun markAuthorViewed(@Path("id") authorId: Int): MarkViewedResponse

    @GET("like-status/{id}")
    suspend fun getLikeStatus(@Path("id") videoId: Int): LikeStatusResponse

    @POST("like/{id}")
    suspend fun toggleLike(@Path("id") videoId: Int): LikeResponse

    @GET("subscription/{id}")
    suspend fun getSubscriptionStatus(@Path("id") authorId: Int): SubscriptionStatusResponse

    @POST("subscribe/{id}")
    suspend fun subscribe(
        @Path("id") authorId: Int,
        @Body body: SubscribeRequest
    ): SubscribeResponse

    @GET("comments/{id}")
    suspend fun getComments(
        @Path("id") videoId: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order") order: String = "new"
    ): CommentsResponse

    @POST("comment/{id}")
    suspend fun postComment(
        @Path("id") videoId: Int,
        @Body body: CommentCreateRequest
    ): CommentCreateResponse

    @POST("comment-like/{id}")
    suspend fun toggleCommentLike(
        @Path("id") commentId: Int
    ): CommentLikeResponse

    @GET("comment/{id}/replies")
    suspend fun getCommentReplies(
        @Path("id") parentCommentId: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): CommentRepliesResponse

    @GET("notifications")
    suspend fun getNotifications(
        @Query("filter") filter: String = "all",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): NotificationsResponse

    @POST("notifications/mark-read/{id}")
    suspend fun markNotificationRead(
        @Path("id") id: String
    ): NotificationReadResponse

    @POST("notifications/mark-all-read")
    suspend fun markAllNotificationsRead(): NotificationUnreadResponse

    @POST("notifications/delete/{id}")
    suspend fun deleteNotification(
        @Path("id") id: String
    ): NotificationReadResponse

    @GET("me/profile")
    suspend fun getMeProfile(): MeProfileResponse

    @GET("me/history")
    suspend fun getMeHistory(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): MeHistoryResponse

    @GET("playlist/{id}")
    suspend fun getPlaylist(
        @Path("id") playlistId: Int
    ): PlaylistFullResponse

    @POST("playlist/{id}/reorder")
    suspend fun reorderPlaylist(
        @Path("id") playlistId: Int,
        @Body body: ReorderRequest
    ): ReorderResponse

    @POST("playlist/{id}/remove/{video_id}")
    suspend fun removeFromPlaylist(
        @Path("id") playlistId: Int,
        @Path("video_id") videoId: Int
    ): RemoveFromPlaylistResponse

    @POST("playlist/create")
    suspend fun createPlaylist(
        @Body body: CreatePlaylistRequest
    ): CreatePlaylistResponse

    @POST("playlist/{id}/add/{video_id}")
    suspend fun addToPlaylist(
        @Path("id") playlistId: Int,
        @Path("video_id") videoId: Int
    ): AddToPlaylistResponse

    @GET("video/{id}/playlists")
    suspend fun getVideoPlaylists(
        @Path("id") videoId: Int
    ): VideoInPlaylistsResponse

    @POST("playlist/{id}/update")
    suspend fun updatePlaylist(
        @Path("id") playlistId: Int,
        @Body body: UpdatePlaylistRequest
    ): UpdatePlaylistResponse

    @POST("playlist/{id}/delete")
    suspend fun deletePlaylist(
        @Path("id") playlistId: Int
    ): DeletePlaylistResponse
}
