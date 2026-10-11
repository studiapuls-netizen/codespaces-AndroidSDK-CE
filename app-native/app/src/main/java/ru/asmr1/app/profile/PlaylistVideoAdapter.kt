package ru.asmr1.app.profile

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.ProfileVideo
import ru.asmr1.app.databinding.ItemPlaylistVideoBinding
import ru.asmr1.app.utils.formatDuration
import java.util.Locale
import kotlin.math.abs

class PlaylistVideoAdapter(
    private var items: MutableList<ProfileVideo> = mutableListOf(),
    private val canEdit: Boolean,
    private val onClick: (ProfileVideo) -> Unit = {},
    private val onDeleteSwipe: (ProfileVideo, View) -> Unit = { _, _ -> },
    private val onReorder: (List<ProfileVideo>) -> Unit = {}
) : RecyclerView.Adapter<PlaylistVideoAdapter.ViewHolder>() {

    companion object {
        private const val DELETE_THRESHOLD_RATIO = 0.35f
        private const val MAX_SWIPE_DP = 300f
        private const val SWIPE_START_DP = 10f
    }

    /** Ссылка на ItemTouchHelper, чтобы стартовать drag прямо из ViewHolder */
    private var touchHelper: ItemTouchHelper? = null

    fun setItems(newItems: List<ProfileVideo>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun getItems(): List<ProfileVideo> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position in items.indices) holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val binding: ItemPlaylistVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val density = binding.root.resources.displayMetrics.density
        private val maxSwipePx = MAX_SWIPE_DP * density
        private val swipeStartPx = SWIPE_START_DP * density

        init {
            if (canEdit) setupTouch()
        }

        fun bind(video: ProfileVideo) {
            binding.plvTitle.text = video.title
            binding.plvAuthor.text = video.authorName
            binding.plvMeta.text = formatViewsShort(video.views)

            if (video.thumbnail.isNotEmpty()) {
                binding.plvThumb.load(video.thumbnail) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            }

            if (video.duration > 0) {
                binding.plvDuration.visibility = View.VISIBLE
                binding.plvDuration.text = formatDuration(video.duration)
            } else {
                binding.plvDuration.visibility = View.GONE
            }

            binding.dragHandle.visibility = if (canEdit) View.VISIBLE else View.GONE

            binding.plVideoCard.translationX = 0f
            binding.plVideoCard.alpha = 1f
            binding.plVideoCard.elevation = 0f
            binding.root.alpha = 1f

            binding.plVideoCard.setOnClickListener { onClick(video) }
        }

        /**
         * Единый обработчик тача на карточке:
         *  - если DOWN попал по drag_handle  → сразу startDrag
         *  - иначе                            → логика свайпа влево
         */
        @SuppressLint("ClickableViewAccessibility")
        private fun setupTouch() {
            val card = binding.plVideoCard
            var startX = 0f
            var startY = 0f
            var isSwiping = false
            var isDragging = false
            var downTime = 0L

            card.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        isSwiping = false
                        isDragging = false
                        downTime = System.currentTimeMillis()

                        // ===== Если тап по ручке — сразу стартуем drag =====
                        if (canEdit && isTouchOnHandle(event)) {
                            isDragging = true
                            val helper = touchHelper
                            if (helper != null) {
                                helper.startDrag(this)
                            }
                            return@setOnTouchListener true
                        }
                        false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Если drag — отдаём управление ItemTouchHelper
                        if (isDragging) return@setOnTouchListener true

                        val dx = event.rawX - startX
                        val dy = event.rawY - startY

                        if (!isSwiping) {
                            if (dx < -swipeStartPx && abs(dx) > abs(dy) * 1.5f) {
                                isSwiping = true
                                card.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }

                        if (isSwiping) {
                            card.translationX = dx.coerceIn(-maxSwipePx, 0f)
                            true
                        } else {
                            false
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            isDragging = false
                            return@setOnTouchListener true
                        }

                        if (isSwiping) {
                            val dx = card.translationX
                            val threshold = -card.width * DELETE_THRESHOLD_RATIO
                            val position = bindingAdapterPosition
                            val video = if (position in items.indices) items[position] else null

                            if (dx < threshold && video != null) {
                                card.animate()
                                    .translationX(-card.width.toFloat())
                                    .alpha(0f)
                                    .setDuration(180)
                                    .withEndAction {
                                        onDeleteSwipe(video, card)
                                    }
                                    .start()
                            } else {
                                card.animate()
                                    .translationX(0f)
                                    .setDuration(180)
                                    .start()
                            }
                            card.parent?.requestDisallowInterceptTouchEvent(false)
                            isSwiping = false
                            true
                        } else {
                            val elapsed = System.currentTimeMillis() - downTime
                            if (elapsed < 400) {
                                val position = bindingAdapterPosition
                                if (position in items.indices) onClick(items[position])
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
                        isDragging = false
                        false
                    }

                    else -> false
                }
            }
        }

        private fun isTouchOnHandle(event: MotionEvent): Boolean {
            val handle = binding.dragHandle
            if (handle.width == 0 || handle.height == 0) return false

            // Координаты тача относительно самой карточки
            val cardLoc = IntArray(2)
            binding.plVideoCard.getLocationOnScreen(cardLoc)
            val localX = event.rawX - cardLoc[0]
            val localY = event.rawY - cardLoc[1]

            // Координаты ручки относительно карточки (тот же родитель → local всегда в одном фрейме)
            val hx = handle.left.toFloat()
            val hy = handle.top.toFloat()
            val hw = handle.width.toFloat()
            val hh = handle.height.toFloat()

            // Запас 8dp, чтобы палец не промахивался
            val padding = 8 * density

            return localX >= hx - padding && localX <= hx + hw + padding &&
                    localY >= hy - padding && localY <= hy + hh + padding
        }
    }

    private fun formatViewsShort(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> "$n"
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            removeDuration = 200
            addDuration = 150
            moveDuration = 200
            changeDuration = 150
        }

        if (!canEdit) return

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun getMovementFlags(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder
            ): Int = makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            )

            override fun isLongPressDragEnabled(): Boolean = false
            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (from !in items.indices || to !in items.indices) return false

                val moved = items.removeAt(from)
                items.add(to, moved)
                notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.alpha = 1f
                vh.itemView.elevation = 0f
                vh.itemView.translationX = 0f
                vh.itemView.translationY = 0f
                vh.itemView.scaleX = 1f
                vh.itemView.scaleY = 1f

                onReorder(items.toList())
            }

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    vh.itemView.alpha = 0.9f
                    vh.itemView.elevation = 20f
                    vh.itemView.scaleX = 1.02f
                    vh.itemView.scaleY = 1.02f
                }
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val helper = ItemTouchHelper(callback)
        helper.attachToRecyclerView(recyclerView)
        touchHelper = helper
    }
}
