package ru.asmr1.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity
import ru.asmr1.app.data.Video
import ru.asmr1.app.ui.FeedFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FeedFragment())
                .commit()
        }

        setupAutoPip()
    }

    fun openPlayer(video: Video) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ru.asmr1.app.player.PlayerModalFragment.newInstance(video))
            .addToBackStack("player")
            .commit()
    }

    fun _oldOpenPlayer(video: Video) {
        // TODO Этап 2
    }

    private fun setupAutoPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(true)
                    .setSeamlessResizeEnabled(true)
                    .build()
                setPictureInPictureParams(params)
            } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }
}
