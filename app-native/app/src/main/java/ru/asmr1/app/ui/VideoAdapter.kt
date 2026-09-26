package ru.asmr1.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ru.asmr1.app.R
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.ItemVideoBinding

class VideoAdapter(
    private var videos: List<Video> = emptyList(),
    private val onClick: (Video) -> Unit = {}
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    fun setVideos(newVideos: List<Video>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    fun appendVideos(newVideos: List<Video>) {
        val startPos = videos.size
        videos = videos + newVideos
        notifyItemRangeInserted(startPos, newVideos.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount(): Int = videos.size

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            // Название
            binding.title.text = video.title

            // Мета: автор • просмотры
            val viewsText = formatViews(video.views)
            binding.meta.text = "${video.author.name} • $viewsText"

            // Превью
            if (video.thumbnail.isNotEmpty()) {
                binding.thumbnail.load(video.thumbnail) {
                    crossfade(true)
                    placeholder(R.color.card_bg)
                }
            }

            // Аватар (круглый)
            if (video.author.avatar.isNotEmpty()) {
                binding.authorAvatar.load(video.author.avatar) {
                    crossfade(true)
                    placeholder(R.color.card_bg)
                }
            }

            // Длительность
            if (video.duration > 0) {
                binding.duration.visibility = View.VISIBLE
                binding.duration.text = formatDuration(video.duration)
            } else {
                binding.duration.visibility = View.GONE
            }

            // Клик
            binding.root.setOnClickListener { onClick(video) }
        }
    }

    private fun formatViews(n: Int): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM просмотров", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK просмотров", n / 1_000.0)
            else -> "$n просмотров"
        }
    }

    private fun formatDuration(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "$m:${if (s < 10) "0$s" else s}"
    }
}
