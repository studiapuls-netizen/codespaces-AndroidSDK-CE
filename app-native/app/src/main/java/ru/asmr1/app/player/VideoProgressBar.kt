package ru.asmr1.app.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Только полоса прогресса. Thumb рисуется отдельным View поверх (thumb_view).
 * Сообщает координаты thumb через [onThumbPosition].
 */
class VideoProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var buffered = 0f
    private var expanded = false
    private var cornersEnabled = false

    private val thinHeightPx = dpToPx(2f)
    private val thickHeightPx = dpToPx(3.5f)
    private val cornerPx = dpToPx(2f)

    // Высота зоны кликов
    private val touchAreaPx = dpToPx(24f)

    private val bgColor = Color.parseColor("#66000000")
    private val progressColor = Color.WHITE

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = progressColor
        style = Paint.Style.FILL
    }

    private val tmpRect = RectF()

    var onSeek: ((Float) -> Unit)? = null
    var onSeekStart: (() -> Unit)? = null
    var onSeekEnd: (() -> Unit)? = null

    /** Callback для перемещения внешнего thumb: (x, y) в координатах этого View. */
    var onThumbPosition: ((Float, Float) -> Unit)? = null

    private var isDragging = false

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
        notifyThumbPosition()
    }

    fun setBuffered(b: Float) {
        buffered = b.coerceIn(0f, 1f)
        invalidate()
    }

    fun setExpanded(expand: Boolean) {
        expanded = expand
        invalidate()
    }

    fun setCornersEnabled(enabled: Boolean) {
        if (cornersEnabled == enabled) return
        cornersEnabled = enabled
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = touchAreaPx.toInt()
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        val w = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val barH = if (expanded) thickHeightPx else thinHeightPx

        // Полоса в самом низу View
        val barBottom = h
        val barTop = h - barH

        val r = if (cornersEnabled) cornerPx else 0f

        // Фон полосы
        tmpRect.set(0f, barTop, w, barBottom)
        canvas.drawRoundRect(tmpRect, r, r, bgPaint)

        // Прогресс
        val progressW = w * progress
        if (progressW > 0f) {
            tmpRect.set(0f, barTop, progressW, barBottom)
            canvas.drawRoundRect(tmpRect, r, r, progressPaint)
        }

        // Thumb рисуется ВНЕШНИМ View, но координаты сообщаем отсюда
        notifyThumbPosition()
    }

    private fun notifyThumbPosition() {
        if (width == 0 || height == 0) return
        val barH = if (expanded) thickHeightPx else thinHeightPx
        val barBottom = height.toFloat()
        val barTop = barBottom - barH
        val cx = (width * progress).coerceIn(0f, width.toFloat())
        // Центр thumb на полосе
        val cy = barTop + barH / 2f
        onThumbPosition?.invoke(cx, cy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onSeekStart?.invoke()
                handleTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    handleTouch(event.x)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onSeekEnd?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(x: Float) {
        val p = (x / width).coerceIn(0f, 1f)
        progress = p
        invalidate()
        notifyThumbPosition()
        onSeek?.invoke(p)
    }

    fun setDragging(dragging: Boolean) {
        isDragging = dragging
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}
