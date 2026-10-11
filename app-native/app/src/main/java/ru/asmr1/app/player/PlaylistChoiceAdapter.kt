package ru.asmr1.app.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.asmr1.app.R
import ru.asmr1.app.data.PlaylistCardItem
import ru.asmr1.app.databinding.ItemPlaylistChoiceBinding

class PlaylistChoiceAdapter(
    private var items: List<PlaylistCardItem> = emptyList(),
    private var containsIds: Set<Int> = emptySet(),
    private val onClick: (PlaylistCardItem) -> Unit = {}
) : RecyclerView.Adapter<PlaylistChoiceAdapter.VH>() {

    fun setItems(newItems: List<PlaylistCardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setContainsIds(ids: Set<Int>) {
        containsIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistChoiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemPlaylistChoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlaylistCardItem) {
            binding.tvTitle.text = item.title
            binding.tvCount.text = "${item.count} видео"

            val isPrivate = item.visibility == "private"
            val isInPlaylist = containsIds.contains(item.id)

            // Если видео уже в этом плейлисте — иконка "замок/папка с галочкой"
            binding.ivFolder.setImageResource(
                when {
                    isInPlaylist -> R.drawable.ic_folder_check
                    isPrivate -> R.drawable.ic_folder_private
                    else -> R.drawable.ic_folder_public
                }
            )

            // Галочка справа — только если видео уже добавлено
            binding.ivCheck.visibility = if (isInPlaylist) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
