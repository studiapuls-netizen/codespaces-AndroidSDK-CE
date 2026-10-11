package ru.asmr1.app.download

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.asmr1.app.data.Video
import java.io.File

/**
 * Хранилище метаданных о скачанных файлах.
 * Файлы — context.filesDir/downloads/
 * Метаданные — SharedPreferences (JSON).
 *
 * Кэш индекса в памяти — чтобы не парсить JSON на каждый вызов hasVideo()/hasAudio().
 */
class DownloadStorage(context: Context) {

    private val prefs = context.getSharedPreferences("asmr1_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val appContext = context.applicationContext

    companion object {
        private const val KEY_INDEX = "downloads_index"

        fun getDownloadsDir(context: Context): File {
            val dir = File(context.filesDir, "downloads")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        // ===== КЭШ ИНДЕКСА В ПАМЯТИ (ОБЩИЙ ДЛЯ ВСЕХ ЭКЗЕМПЛЯРОВ) =====
        @Volatile
        private var cachedIndex: MutableMap<String, DownloadedFile>? = null

        private fun invalidateCache() {
            cachedIndex = null
        }
    }

    private fun readIndex(): MutableMap<String, DownloadedFile> {
        // Если кэш есть — возвращаем копию (чтобы не мутировали снаружи)
        cachedIndex?.let { return HashMap(it) }

        val json = prefs.getString(KEY_INDEX, null) ?: run {
            val empty = mutableMapOf<String, DownloadedFile>()
            cachedIndex = empty
            return HashMap(empty)
        }

        val parsed = try {
            val type = object : TypeToken<MutableMap<String, DownloadedFile>>() {}.type
            gson.fromJson<MutableMap<String, DownloadedFile>>(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }

        cachedIndex = parsed
        return HashMap(parsed)
    }

    private fun writeIndex(index: Map<String, DownloadedFile>) {
        prefs.edit().putString(KEY_INDEX, gson.toJson(index)).apply()
        // Обновляем кэш — теперь он актуален
        cachedIndex = HashMap(index)
    }

    private fun key(videoId: Int, type: DownloadedFile.Type): String =
        "${videoId}_${type.name.lowercase()}"

    // ===== Чтение =====

    fun getFile(videoId: Int, type: DownloadedFile.Type): DownloadedFile? {
        val index = readIndex()
        val entry = index[key(videoId, type)] ?: return null
        val f = File(getDownloadsDir(appContext), entry.fileName)
        return if (f.exists()) entry else {
            index.remove(key(videoId, type))
            writeIndex(index)
            null
        }
    }

    fun hasVideo(videoId: Int): Boolean = getFile(videoId, DownloadedFile.Type.VIDEO) != null
    fun hasAudio(videoId: Int): Boolean = getFile(videoId, DownloadedFile.Type.AUDIO) != null

    fun hasAnyDownloads(): Boolean = readIndex().isNotEmpty()

    fun getAllDownloads(): List<DownloadedFile> {
        val all = readIndex().values.filter { entry ->
            val f = File(getDownloadsDir(appContext), entry.fileName)
            f.exists()
        }
        return all.sortedByDescending { it.downloadedAt }
    }

    fun getDownloads(type: DownloadedFile.Type? = null): List<DownloadedFile> {
        val all = getAllDownloads()
        return if (type == null) all else all.filter { it.type == type }
    }

    // ===== Запись =====

    fun saveFile(video: Video, type: DownloadedFile.Type, file: File) {
        val index = readIndex()
        index[key(video.id, type)] = DownloadedFile(
            videoId = video.id,
            type = type,
            fileName = file.name,
            size = file.length(),
            downloadedAt = System.currentTimeMillis(),
            title = video.title,
            description = video.description,
            thumbnail = video.thumbnail,
            duration = video.duration,
            views = video.views,
            likes = video.likes,
            published = video.published,
            videoUrl = video.videoUrl,
            audioUrl = video.audioUrl,
            authorId = video.author.id,
            authorName = video.author.name,
            authorAvatar = video.author.avatar
        )
        writeIndex(index)
    }

    fun deleteFile(videoId: Int, type: DownloadedFile.Type): Boolean {
        val entry = getFile(videoId, type) ?: return false
        val file = File(getDownloadsDir(appContext), entry.fileName)
        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            val index = readIndex()
            index.remove(key(videoId, type))
            writeIndex(index)
        }
        return deleted
    }

    fun getFilePath(videoId: Int, type: DownloadedFile.Type): File? {
        val entry = getFile(videoId, type) ?: return null
        return File(getDownloadsDir(appContext), entry.fileName)
    }
}
