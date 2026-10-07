package ru.asmr1.app.player

import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper

/**
 * Обёртка над LoudnessEnhancer.
 * - Усиливает звук до [targetGainMb] миллибел.
 * - Плавный fade-in с 0 до целевого усиления за [FADE_IN_MS] мс.
 */
class AudioBoost {

    companion object {
        // ×3 по амплитуде ≈ +9.5 дБ ≈ 950 mB
        private const val TARGET_GAIN_MB = 950
        private const val FADE_IN_MS = 1500L
        private const val FADE_STEP_MS = 30L
    }

    private var enhancer: LoudnessEnhancer? = null
    private var currentGainMb = 0
    private var fadeRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private var targetAudioSessionId: Int = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (enhancer != null && targetAudioSessionId == audioSessionId) return

        release()
        targetAudioSessionId = audioSessionId

        try {
            enhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(0)
                enabled = true
            }
        } catch (_: Exception) {
            enhancer = null
        }
    }

    /** Плавно увеличить громкость с 0 до TARGET_GAIN_MB за FADE_IN_MS. */
    fun fadeIn() {
        cancelFade()
        val e = enhancer ?: return

        currentGainMb = 0
        try {
            e.setTargetGain(0)
        } catch (_: Exception) {
        }

        val steps = (FADE_IN_MS / FADE_STEP_MS).toInt().coerceAtLeast(1)
        val stepGain = TARGET_GAIN_MB / steps
        var step = 0

        val r = object : Runnable {
            override fun run() {
                val e2 = enhancer ?: return
                step++
                currentGainMb = (currentGainMb + stepGain).coerceAtMost(TARGET_GAIN_MB)
                try {
                    e2.setTargetGain(currentGainMb)
                } catch (_: Exception) {
                }
                if (step < steps) {
                    handler.postDelayed(this, FADE_STEP_MS)
                }
            }
        }
        fadeRunnable = r
        handler.postDelayed(r, FADE_STEP_MS)
    }

    fun setGain(gainMb: Int) {
        cancelFade()
        val e = enhancer ?: return
        currentGainMb = gainMb.coerceIn(0, TARGET_GAIN_MB)
        try {
            e.setTargetGain(currentGainMb)
        } catch (_: Exception) {
        }
    }

    private fun cancelFade() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
    }

    fun release() {
        cancelFade()
        try {
            enhancer?.enabled = false
            enhancer?.release()
        } catch (_: Exception) {
        }
        enhancer = null
        currentGainMb = 0
        targetAudioSessionId = 0
    }
}
