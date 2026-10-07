package ru.asmr1.app.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Расширяющийся круг из точки нажатия, затухающий со временем.
 */
class RippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var maxRadius = 0f
    private var alpha = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private var animator: ValueAnimator? = null

    fun start(touchX: Float, touchY: Float) {
        centerX = touchX
        centerY = touchY
        maxRadius = Math.min(width.toFloat(), height.toFloat()) * 0.6f
        radius = 0f
        alpha = 0.35f

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                radius = maxRadius * v
                alpha = 0.35f * (1f - v)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f || alpha <= 0f) return
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }
}
