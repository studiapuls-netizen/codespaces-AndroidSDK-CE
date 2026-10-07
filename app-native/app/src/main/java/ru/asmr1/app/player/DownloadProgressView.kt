package ru.asmr1.app.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Оверлей прогресса — заполняется снизу вверх полупрозрачным красным.
 */
class DownloadProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Float = 0f
    private var visible: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80CF001F")
        style = Paint.Style.FILL
    }

    private val rect = RectF()

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        visible = progress > 0f && progress < 1f
        invalidate()
    }

    fun setProgressPercent(p: Int) {
        setProgress(p / 100f)
    }

    fun hide() {
        visible = false
        progress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!visible) return

        val w = width.toFloat()
        val h = height.toFloat()
        val fillHeight = h * progress
        val top = h - fillHeight

        rect.set(0f, top, w, h)
        val radius = (w.coerceAtMost(h) * 0.2f)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}
