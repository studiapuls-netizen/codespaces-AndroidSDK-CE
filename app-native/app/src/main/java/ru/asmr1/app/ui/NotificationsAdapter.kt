package ru.asmr1.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.NotificationItem
import ru.asmr1.app.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class NotificationsAdapter(
    private var items: List<NotificationItem> = emptyList(),
    private val onClick: (NotificationItem) -> Unit = {},
    private val onDeleteSwipe: (NotificationItem, View) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<NotificationsAdapter.NotifViewHolder>() {

    companion object {
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private const val DELETE_THRESHOLD_RATIO = 0.35f
        private const val MAX_SWIPE_DP = 200f
        private const val SWIPE_START_DP = 10f  // минимальный порог для старта свайпа
    }

    fun setItems(newItems: List<NotificationItem>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    fun appendItems(newItems: List<NotificationItem>) {
        if (newItems.isEmpty()) return
        val start = items.size
        items = items + newItems
        notifyItemRangeInserted(start, newItems.size)
    }

    fun getItems(): List<NotificationItem> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NotifViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotifViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class NotifViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val density = binding.root.resources.displayMetrics.density
        private val maxSwipePx = MAX_SWIPE_DP * density
        private val swipeStartPx = SWIPE_START_DP * density

        init {
            setupSwipe()
        }

        fun bind(item: NotificationItem) {
            if (item.authorAvatar.isNotEmpty()) {
                binding.notifAvatar.load(item.authorAvatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            }

            if (item.type == "reply") {
                binding.notifText.text = "${item.authorName} ответил на ваш комментарий"
                if (item.commentText.isNotEmpty()) {
                    binding.notifCommentText.visibility = View.VISIBLE
                    binding.notifCommentText.text = "«${item.commentText}»"
                } else {
                    binding.notifCommentText.visibility = View.GONE
                }
            } else {
                binding.notifText.text = "${item.authorName} опубликовал новое видео: «${item.title}»"
                binding.notifCommentText.visibility = View.GONE
            }

            binding.notifDate.text = formatTimeAgo(item.timestamp)

            if (item.thumbnail.isNotEmpty()) {
                binding.notifThumb.visibility = View.VISIBLE
                binding.notifThumb.load(item.thumbnail) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            } else {
                binding.notifThumb.visibility = View.INVISIBLE
            }

            binding.notifRoot.setBackgroundColor(
                if (item.read) Color.TRANSPARENT else 0x1AFFFFFF
            )

            binding.notifRoot.translationX = 0f
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setupSwipe() {
            val card = binding.notifRoot
            var startX = 0f
            var startY = 0f
            var isSwiping = false
            var downTime = 0L

            card.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        isSwiping = false
                        downTime = System.currentTimeMillis()
                        // Не блокируем сразу — даём RecyclerView шанс на вертикальный скролл
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY

                        // Проверяем: горизонтальный ли свайп
                        if (!isSwiping) {
                            // Если палец сместился вправо больше чем на swipeStartPx
                            // и движение явно горизонтальное
                            if (dx > swipeStartPx && dx > abs(dy) * 1.5f) {
                                isSwiping = true
                                // Отключаем перехват у RecyclerView, чтобы он не мешал
                                card.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }

                        if (isSwiping) {
                            // Ограничиваем только по макс. ширине
                            card.translationX = dx.coerceIn(0f, maxSwipePx)
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isSwiping) {
                            val dx = card.translationX
                            val threshold = card.width * DELETE_THRESHOLD_RATIO
                            val item = items.getOrNull(bindingAdapterPosition)
                            if (dx > threshold && item != null) {
                                // Удаляем — свайпаем дальше за экран, потом onDeleteSwipe
                                card.animate()
                                    .translationX(card.width.toFloat())
                                    .alpha(0f)
                                    .setDuration(200)
                                    .withEndAction {
                                        onDeleteSwipe(item, card)
                                    }
                                    .start()
                            } else {
                                // Возвращаем
                                card.animate()
                                    .translationX(0f)
                                    .setDuration(180)
                                    .start()
                            }
                            card.parent?.requestDisallowInterceptTouchEvent(false)
                            isSwiping = false
                            true
                        } else {
                            // Обычный тап — не свайпали
                            val elapsed = System.currentTimeMillis() - downTime
                            if (elapsed < 400) {
                                val item = items.getOrNull(bindingAdapterPosition)
                                if (item != null) {
                                    onClick(item)
                                }
                            }
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (isSwiping) {
                            card.animate()
                                .translationX(0f)
                                .setDuration(180)
                                .start()
                            card.parent?.requestDisallowInterceptTouchEvent(false)
                            isSwiping = false
                        }
                        false
                    }
                    else -> false
                }
            }
        }
    }

    private fun formatTimeAgo(gmtDate: String): String {
        if (gmtDate.isEmpty()) return ""
        return try {
            val date = synchronized(dateFmt) { dateFmt.parse(gmtDate) } ?: return gmtDate
            val diff = (System.currentTimeMillis() - date.time) / 1000
            when {
                diff < 60 -> "только что"
                diff < 3600 -> "${diff / 60} мин назад"
                diff < 86400 -> "${diff / 3600} ч назад"
                diff < 2_592_000 -> "${diff / 86400} дн назад"
                diff < 31_536_000 -> "${diff / 2_592_000} мес назад"
                else -> "${diff / 31_536_000} г назад"
            }
        } catch (_: Exception) {
            gmtDate
        }
    }
}
