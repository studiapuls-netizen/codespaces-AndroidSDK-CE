package ru.asmr1.app.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import ru.asmr1.app.R

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTHOR = "author"
        const val EXTRA_START_TIME_MS = "start_time_ms"
    }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private var videoId: Int = 0
    private var startTimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Данные
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        videoId = intent.getIntExtra(EXTRA_VIDEO_ID, 0)
        startTimeMs = intent.getLongExtra(EXTRA_START_TIME_MS, 0L)

        // Кнопка закрытия
        findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            finish()
        }

        // ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView = findViewById(R.id.player_view)
        playerView.player = player

        if (videoUrl.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(videoUrl)
            player.setMediaItem(mediaItem, startTimeMs)
            player.prepare()
            player.playWhenReady = true
        }

        // Auto-PiP: система сама переведёт в PiP при жесте Home (Android 12+)
        setupAutoPip()

        // Когда видео закончилось — закрываем
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    finish()
                }
            }
        })
    }

    /**
     * ★ ГЛАВНОЕ: включаем авто-PiP.
     * Работает на Android 12+ (включая HyperOS, MIUI).
     */
    private fun setupAutoPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(true)
                    .setSeamlessResizeEnabled(true)
                    .build()
                setPictureInPictureParams(params)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Скрываем кнопку закрытия в PiP
        findViewById<ImageButton>(R.id.btn_close).visibility =
            if (isInPictureInPictureMode) android.view.View.GONE else android.view.View.VISIBLE
    }

    override fun onStop() {
        super.onStop()
        // Паузим если уходим в фон (кроме PiP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) {
            player.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
