package ru.asmr1.app.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.ProfileVideo
import ru.asmr1.app.databinding.ItemHistoryCardBinding
import ru.asmr1.app.utils.formatDuration
import java.util.Locale

class HistoryCardAdapter(
    private var items: List<ProfileVideo> = emptyList(),
    private val onClick: (ProfileVideo) -> Unit = {}
) : RecyclerView.Adapter<HistoryCardAdapter.HistoryViewHolder>() {

    fun setItems(newItems: List<ProfileVideo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemHistoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: ProfileVideo) {
            binding.histTitle.text = video.title
            binding.histMeta.text = formatViewsShort(video.views)

            if (video.thumbnail.isNotEmpty()) {
                binding.histThumb.load(video.thumbnail) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            }

            if (video.duration > 0) {
                binding.histDuration.visibility = View.VISIBLE
                binding.histDuration.text = formatDuration(video.duration)
            } else {
                binding.histDuration.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(video) }
        }
    }

    private fun formatViewsShort(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> "$n"
    }
}
