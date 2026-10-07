package ru.asmr1.app.download

import ru.asmr1.app.data.Author
import ru.asmr1.app.data.Video

/**
 * Что скачано для конкретного видео.
 * Хранит все данные о видео, чтобы работал офлайн-просмотр.
 */
data class DownloadedFile(
    val videoId: Int,
    val type: Type,
    val fileName: String,
    val size: Long,
    val downloadedAt: Long,

    // ===== Полные данные видео для офлайн-режима =====
    val title: String = "",
    val description: String = "",
    val thumbnail: String = "",
    val duration: Int = 0,
    val views: Int = 0,
    val likes: Int = 0,
    val published: String = "",
    val videoUrl: String = "",
    val audioUrl: String? = null,
    val authorId: Int = 0,
    val authorName: String = "",
    val authorAvatar: String = ""
) {
    enum class Type { VIDEO, AUDIO }

    /**
     * Собрать объект Video для передачи в плеер.
     */
    fun toVideo(): Video {
        return Video(
            id = videoId,
            title = title,
            description = description,
            thumbnail = thumbnail,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            duration = duration,
            views = views,
            likes = likes,
            published = published,
            author = Author(
                id = authorId,
                name = authorName,
                avatar = authorAvatar
            ),
            tags = null
        )
    }
}

data class DownloadProgress(
    val videoId: Int,
    val type: DownloadedFile.Type,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressPercent: Int
) {
    val isDone: Boolean get() = progressPercent >= 100
}
