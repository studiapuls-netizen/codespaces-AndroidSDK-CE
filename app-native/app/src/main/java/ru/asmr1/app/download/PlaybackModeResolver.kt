package ru.asmr1.app.download

import android.content.Context
import android.net.Uri
import ru.asmr1.app.data.Video
import java.io.File

/**
 * Определяет, откуда играть видео:
 * - Скачано видео → локальный .mp4
 * - Скачано аудио → локальный .mp3 (в аудио-режиме)
 * - Ничего не скачано → онлайн URL
 */
object PlaybackModeResolver {

    sealed class Mode {
        /** Играем видео (локально или онлайн) */
        data class VideoMode(val uri: Uri) : Mode()

        /** Играем только аудио — вместо видео показываем превью */
        data class AudioMode(
            val uri: Uri,
            val previewUrl: String
        ) : Mode()
    }

    /**
     * @param video объект видео
     * @return Mode — как играть
     */
    fun resolve(context: Context, video: Video): Mode {
        val storage = DownloadStorage(context)

        val hasVideo = storage.hasVideo(video.id)
        val hasAudio = storage.hasAudio(video.id)

        // Приоритет: локальное видео > локальное аудио > онлайн видео
        return when {
            hasVideo -> {
                val file = storage.getFilePath(video.id, DownloadedFile.Type.VIDEO)
                if (file != null && file.exists()) {
                    Mode.VideoMode(Uri.fromFile(file))
                } else {
                    // Файл пропал — играем онлайн
                    Mode.VideoMode(Uri.parse(video.videoUrl))
                }
            }
            hasAudio -> {
                val file = storage.getFilePath(video.id, DownloadedFile.Type.AUDIO)
                if (file != null && file.exists()) {
                    Mode.AudioMode(
                        uri = Uri.fromFile(file),
                        previewUrl = video.thumbnail
                    )
                } else {
                    Mode.VideoMode(Uri.parse(video.videoUrl))
                }
            }
            else -> {
                // Ничего не скачано — онлайн
                Mode.VideoMode(Uri.parse(video.videoUrl))
            }
        }
    }

    /**
     * Просто определить, играем ли мы локальный файл (полезно для UI).
     */
    fun isOffline(context: Context, videoId: Int): Boolean {
        val storage = DownloadStorage(context)
        return storage.hasVideo(videoId) || storage.hasAudio(videoId)
    }
}
