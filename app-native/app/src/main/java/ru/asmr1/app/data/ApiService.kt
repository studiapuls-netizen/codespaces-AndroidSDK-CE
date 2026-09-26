package ru.asmr1.app.data

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    /**
     * Лента видео
     * @param tab "recommended" или "latest"
     * @param page номер страницы (с 1)
     * @param perPage сколько за раз (по умолч. 20)
     */
    @GET("feed")
    suspend fun getFeed(
        @Query("tab") tab: String = "recommended",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): FeedResponse
}
