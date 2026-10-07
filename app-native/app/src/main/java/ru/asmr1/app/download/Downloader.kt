package ru.asmr1.app.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.asmr1.app.data.Video
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Движок загрузки. Качает во внутреннее хранилище приложения.
 */
class Downloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val storage = DownloadStorage(context)

    /**
     * Скачать файл. При завершении сохраняет полные метаданные видео.
     */
    fun download(
        video: Video,
        type: DownloadedFile.Type,
        url: String,
        extension: String
    ): Flow<DownloadProgress> = flow {

        val existing = storage.getFile(video.id, type)
        if (existing != null) {
            emit(DownloadProgress(video.id, type, existing.size, existing.size, 100))
            return@flow
        }

        val dir = DownloadStorage.getDownloadsDir(context)
        val finalName = "${type.name.lowercase()}_${video.id}.${extension}"
        val tmpName = "${finalName}.part"
        val tmpFile = File(dir, tmpName)
        val finalFile = File(dir, finalName)

        if (tmpFile.exists()) tmpFile.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ASMR1-Android/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            val body = response.body ?: throw RuntimeException("Empty body")

            val total = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 200 || (total > 0 && downloaded >= total)) {
                            lastEmit = now
                            val percent = if (total > 0) {
                                ((downloaded.toDouble() / total) * 100).toInt().coerceIn(0, 100)
                            } else 0
                            emit(DownloadProgress(video.id, type, downloaded, if (total > 0) total else 0, percent))
                        }
                    }
                }
            }
        }

        if (finalFile.exists()) finalFile.delete()
        val ok = tmpFile.renameTo(finalFile)
        if (!ok) {
            tmpFile.copyTo(finalFile, overwrite = true)
            tmpFile.delete()
        }

        storage.saveFile(video, type, finalFile)

        emit(DownloadProgress(video.id, type, finalFile.length(), finalFile.length(), 100))
    }.flowOn(Dispatchers.IO)

    fun delete(videoId: Int, type: DownloadedFile.Type): Boolean {
        return storage.deleteFile(videoId, type)
    }

    fun getRemoteFileSize(url: String): Long {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "ASMR1-Android/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return -1
                response.header("Content-Length")?.toLongOrNull() ?: -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}
