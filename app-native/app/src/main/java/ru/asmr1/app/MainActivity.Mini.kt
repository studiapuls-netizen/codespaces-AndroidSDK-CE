package ru.asmr1.app

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Mini-плеер: жесты перетаскивания и pinch-to-zoom.
 *
 * Extension-функции для MainActivity.
 * Константы CARD_RATIO / CONTROLS_DP / CONTROLS_MARGIN_DP теперь top-level в MainActivity.kt.
 */

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupMiniPlayerGestures() {
    var startX = 0f
    var startY = 0f
    var startTouchX = 0f
    var startTouchY = 0f
    var isDragging = false
    var downTime = 0L

    var isPinching = false
    var pinchStartDist = 0f
    var pinchStartWidth = 0
    var pinchCenterX = 0f
    var pinchCenterY = 0f

    miniPlayerWrap.setOnTouchListener { v, event ->
        val parent = v.parent as? ViewGroup ?: return@setOnTouchListener false
        val parentW = parent.width.toFloat()
        val parentH = parent.height.toFloat()
        val marginF = marginPx.toFloat()
        val minWidthPx = (minWidthDp * density).toInt()
        val maxWidthPx = (maxWidthDp * density).toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = v.x
                startY = v.y
                startTouchX = event.rawX
                startTouchY = event.rawY
                isDragging = false
                downTime = System.currentTimeMillis()
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    isPinching = true
                    isDragging = false
                    pinchStartDist = distanceBetween(event, 0, 1)
                    pinchStartWidth = v.width
                    pinchCenterX = v.x + v.width / 2f
                    pinchCenterY = v.y + v.height / 2f
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPinching && event.pointerCount >= 2) {
                    val curDist = distanceBetween(event, 0, 1)
                    if (pinchStartDist > 0f && pinchStartWidth > 0) {
                        val factor = curDist / pinchStartDist
                        var newW = (pinchStartWidth * factor).toInt()
                        newW = newW.coerceIn(minWidthPx, maxWidthPx)
                        val newH = wrapHeightForWidth(newW)
                        val maxX = (parentW - newW - marginPx).coerceAtLeast(marginF)
                        val maxY = (parentH - newH - marginPx).coerceAtLeast(marginF)
                        val lp = v.layoutParams
                        lp.width = newW
                        lp.height = newH
                        v.layoutParams = lp
                        v.x = (pinchCenterX - newW / 2f).coerceIn(marginF, maxX)
                        v.y = (pinchCenterY - newH / 2f).coerceIn(marginF, maxY)
                    }
                    return@setOnTouchListener true
                }
                val maxX = (parentW - v.width - marginPx).coerceAtLeast(marginF)
                val maxY = (parentH - v.height - marginPx).coerceAtLeast(marginF)
                val dx = event.rawX - startTouchX
                val dy = event.rawY - startTouchY
                if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                v.x = (startX + dx).coerceIn(marginF, maxX)
                v.y = (startY + dy).coerceIn(marginF, maxY)
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (isPinching) {
                    val idx = event.actionIndex
                    val remainIdx = if (idx == 0) 1 else 0
                    if (remainIdx < event.pointerCount) {
                        startTouchX = event.getRawX(remainIdx)
                        startTouchY = event.getRawY(remainIdx)
                        startX = v.x
                        startY = v.y
                    }
                    isPinching = false
                    isDragging = false
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && System.currentTimeMillis() - downTime < 250) {
                    expandFromMini()
                } else if (isDragging) {
                    val maxX = (parentW - v.width - marginPx).coerceAtLeast(marginF)
                    val maxY = (parentH - v.height - marginPx).coerceAtLeast(marginF)
                    val cx = v.x + v.width / 2f
                    val cy = v.y + v.height / 2f
                    val targetX = if (cx < parentW / 2f) marginF else maxX
                    val targetY = if (cy < parentH / 2f) marginF else maxY
                    v.animate().x(targetX).y(targetY).setDuration(200)
                        .withEndAction { refreshPipSourceRect() }.start()
                }
                isDragging = false
                isPinching = false
                refreshPipSourceRect()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isPinching = false
                refreshPipSourceRect()
                true
            }
            else -> false
        }
    }

    val blockDrag = View.OnTouchListener { btn, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> btn.parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> btn.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
    miniBtnClose.setOnTouchListener(blockDrag)
    miniBtnPrev.setOnTouchListener(blockDrag)
    miniBtnPlayPause.setOnTouchListener(blockDrag)
    miniBtnNext.setOnTouchListener(blockDrag)
}

internal fun MainActivity.wrapHeightForWidth(width: Int): Int {
    val cardH = (width * CARD_RATIO).toInt()
    val controlsH = (CONTROLS_DP * density).toInt()
    val marginH = (CONTROLS_MARGIN_DP * density).toInt()
    return cardH + marginH + controlsH
}

internal fun MainActivity.distanceBetween(event: MotionEvent, i1: Int, i2: Int): Float {
    val dx = event.getX(i1) - event.getX(i2)
    val dy = event.getY(i1) - event.getY(i2)
    return hypot(dx, dy)
}
