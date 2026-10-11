package ru.asmr1.app.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.ProfileVideo
import ru.asmr1.app.databinding.ItemHistoryVideoBinding
import ru.asmr1.app.utils.formatDuration
import java.util.Locale

class HistoryAdapter(
    private var items: List<ProfileVideo> = emptyList(),
    private val onClick: (ProfileVideo) -> Unit = {},
    private val onRemove: (ProfileVideo) -> Unit = {}
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    fun setItems(newItems: List<ProfileVideo>) {
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

    fun getItems(): List<ProfileVideo> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemHistoryVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: ProfileVideo) {
            binding.hvTitle.text = video.title
            binding.hvAuthor.text = video.authorName
            binding.hvMeta.text = formatViewsShort(video.views)

            if (video.thumbnail.isNotEmpty()) {
                binding.hvThumb.load(video.thumbnail) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            }

            if (video.duration > 0) {
                binding.hvDuration.visibility = View.VISIBLE
                binding.hvDuration.text = formatDuration(video.duration)
            } else {
                binding.hvDuration.visibility = View.GONE
            }

            // Прогресс пока не приходит — скрываем. Позже можно добавить.
            binding.hvProgress.visibility = View.GONE

            binding.root.setOnClickListener { onClick(video) }
            binding.hvRemove.setOnClickListener { onRemove(video) }
        }
    }

    private fun formatViewsShort(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> "$n"
    }
}
