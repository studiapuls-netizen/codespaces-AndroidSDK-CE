package ru.asmr1.app.data

data class PlaylistCardItem(
    val id: Int,
    val title: String,
    val count: Int,
    val type: String, // "watchlater" | "liked" | "custom"
    val visibility: String? = null
)
