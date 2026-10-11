package ru.asmr1.app.player

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Свайп вправо по карточке комментария = «Ответить».
 *
 * Использует RecyclerView.OnItemTouchListener, чтобы перехватывать
 * горизонтальный свайп до того, как RecyclerView начнёт вертикальный скролл.
 */
class SwipeReplyHelper(
    private val recycler: RecyclerView,
    private val thresholdDp: Float = 60f,
    private val maxSwipeDp: Float = 120f,
    private val onReply: (position: Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private val density = recycler.resources.displayMetrics.density
    private val thresholdPx = thresholdDp * density
    private val maxSwipePx = maxSwipeDp * density

    private var startX = 0f
    private var startY = 0f
    private var swipingView: View? = null
    private var isSwiping = false

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x
                startY = e.y
                isSwiping = false
                swipingView = null
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipingView == null) {
                    val dx = e.x - startX
                    val dy = e.y - startY
                    if (dx > 20f && dx > abs(dy) * 2f) {
                        // Нашли child под пальцем в момент DOWN
                        val child = rv.findChildViewUnder(startX, startY)
                        if (child != null) {
                            val card = child.findViewById<View>(ru.asmr1.app.R.id.comment_card)
                            if (card != null) {
                                swipingView = card
                                isSwiping = true
                                rv.parent?.requestDisallowInterceptTouchEvent(true)
                                return true
                            }
                        }
                    }
                } else if (isSwiping) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping = false
                swipingView = null
            }
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        val card = swipingView ?: return
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - startX).coerceIn(0f, maxSwipePx)
                card.translationX = dx
            }
            MotionEvent.ACTION_UP -> {
                val dx = card.translationX
                if (dx > thresholdPx) {
                    val child = rv.findChildViewUnder(startX, startY)
                    val pos = child?.let { rv.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
                    if (pos != RecyclerView.NO_POSITION) {
                        onReply(pos)
                    }
                }
                card.animate()
                    .translationX(0f)
                    .setDuration(180)
                    .start()
                rv.parent?.requestDisallowInterceptTouchEvent(false)
                swipingView = null
                isSwiping = false
            }
            MotionEvent.ACTION_CANCEL -> {
                card.animate()
                    .translationX(0f)
                    .setDuration(180)
                    .start()
                rv.parent?.requestDisallowInterceptTouchEvent(false)
                swipingView = null
                isSwiping = false
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}
