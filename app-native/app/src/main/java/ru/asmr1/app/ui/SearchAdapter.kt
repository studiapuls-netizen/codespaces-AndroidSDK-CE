package ru.asmr1.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.ItemVideoSearchBinding
import ru.asmr1.app.databinding.ItemVideoSkeletonSearchBinding
import ru.asmr1.app.utils.formatDuration
import ru.asmr1.app.utils.timeAgo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SearchAdapter(
    private var videos: List<Video> = emptyList(),
    private val onClick: (Video) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_VIDEO = 0
        private const val TYPE_SKELETON = 1
        private const val SKELETON_COUNT = 6

        // Общий SimpleDateFormat (с UTC)
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private var showSkeleton = false

    fun setVideos(newVideos: List<Video>) {
        val wasSkeleton = showSkeleton
        showSkeleton = false

        if (wasSkeleton) {
            videos = newVideos
            notifyDataSetChanged()
            return
        }

        val old = videos
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newVideos.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newVideos[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newVideos[n]
        })
        videos = newVideos
        diff.dispatchUpdatesTo(this)
    }

    fun appendVideos(newVideos: List<Video>) {
        if (newVideos.isEmpty()) return
        val startPos = videos.size
        videos = videos + newVideos
        notifyItemRangeInserted(startPos, newVideos.size)
    }

    fun showSkeletons() {
        showSkeleton = true
        videos = emptyList()
        notifyDataSetChanged()
    }

    fun clear() {
        showSkeleton = false
        videos = emptyList()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (showSkeleton) TYPE_SKELETON else TYPE_VIDEO

    override fun getItemCount(): Int =
        if (showSkeleton) SKELETON_COUNT else videos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SKELETON -> SkeletonViewHolder(
                ItemVideoSkeletonSearchBinding.inflate(inflater, parent, false)
            )
            else -> VideoViewHolder(
                ItemVideoSearchBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is VideoViewHolder -> holder.bind(videos[position])
            is SkeletonViewHolder -> holder.bind()
        }
    }

    private fun formatViewsShort(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    private fun timeAgoShort(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        return try {
            val date = synchronized(dateFmt) { dateFmt.parse(dateStr) } ?: return ""
            val diff = (System.currentTimeMillis() - date.time) / 1000
            when {
                diff < 60 -> "сейчас"
                diff < 3600 -> "${diff / 60} мин"
                diff < 86400 -> "${diff / 3600} ч"
                diff < 2_592_000 -> "${diff / 86400} дн"
                diff < 31_536_000 -> "${diff / 2_592_000} мес"
                else -> "${diff / 31_536_000} г"
            }
        } catch (_: Exception) {
            ""
        }
    }

    inner class VideoViewHolder(private val binding: ItemVideoSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            binding.title.text = video.title
            binding.authorName.text = video.author.name

            // Отдельно просмотры и дата — без слова "просмотров" и без точки
            binding.tvViews.text = formatViewsShort(video.views)
            binding.tvDate.text = timeAgoShort(video.published)

            if (video.thumbnail.isNotEmpty()) {
                binding.thumbnail.load(video.thumbnail) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    allowHardware(true)
                }
            }

            if (video.author.avatar.isNotEmpty()) {
                binding.authorAvatar.load(video.author.avatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    allowHardware(true)
                }
            }

            if (video.duration > 0) {
                binding.duration.visibility = View.VISIBLE
                binding.duration.text = formatDuration(video.duration)
            } else {
                binding.duration.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(video) }
        }
    }

    class SkeletonViewHolder(private val binding: ItemVideoSkeletonSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() { /* ничего */ }
    }
}
