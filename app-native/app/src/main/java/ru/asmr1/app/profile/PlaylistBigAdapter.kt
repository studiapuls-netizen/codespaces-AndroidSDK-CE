package ru.asmr1.app.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.ProfileVideo
import ru.asmr1.app.databinding.ItemPlaylistBigBinding

/**
 * Универсальная карточка плейлиста:
 *  - watchlater / liked / custom
 */
data class PlaylistCard(
    val id: Int,
    val title: String,
    val count: Int,
    val type: String, // "watchlater" | "liked" | "custom"
    val firstVideo: ProfileVideo? = null,
    val visibility: String? = null // "public" | "private" (для custom)
)

class PlaylistBigAdapter(
    private var items: List<PlaylistCard> = emptyList(),
    private val onClick: (PlaylistCard) -> Unit = {}
) : RecyclerView.Adapter<PlaylistBigAdapter.PlaylistViewHolder>() {

    fun setItems(newItems: List<PlaylistCard>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBigBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PlaylistViewHolder(private val binding: ItemPlaylistBigBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: PlaylistCard) {
            binding.plTitle.text = card.title
            binding.plCount.text = "${card.count} видео"

            val first = card.firstVideo
            if (first != null && first.thumbnail.isNotEmpty()) {
                binding.plThumb.load(first.thumbnail) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            } else {
                binding.plThumb.setImageResource(R.color.card_bg)
            }

            binding.root.setOnClickListener { onClick(card) }
        }
    }
}
