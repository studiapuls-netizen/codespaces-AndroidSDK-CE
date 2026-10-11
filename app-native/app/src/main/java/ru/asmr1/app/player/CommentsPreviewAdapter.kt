package ru.asmr1.app.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ru.asmr1.app.R
import ru.asmr1.app.data.Comment
import ru.asmr1.app.databinding.ItemCommentPreviewBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CommentsPreviewAdapter(
    private var items: List<Comment> = emptyList(),
    private val onClick: (Comment) -> Unit = {}
) : RecyclerView.Adapter<CommentsPreviewAdapter.PreviewViewHolder>() {

    companion object {
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun setItems(newItems: List<Comment>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val binding = ItemCommentPreviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PreviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PreviewViewHolder(private val binding: ItemCommentPreviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            binding.previewAuthor.text = comment.authorName
            binding.previewDate.text = formatShortDate(comment.dateGmt)
            binding.previewText.text = comment.content

            if (comment.authorAvatar.isNotEmpty()) {
                binding.previewAvatar.load(comment.authorAvatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                }
            }

            binding.root.setOnClickListener { onClick(comment) }
        }
    }

    private fun formatShortDate(gmtDate: String): String {
        if (gmtDate.isEmpty()) return ""
        return try {
            val date = synchronized(dateFmt) { dateFmt.parse(gmtDate) } ?: return gmtDate
            val diff = (System.currentTimeMillis() - date.time) / 1000
            when {
                diff < 60 -> "сейчас"
                diff < 3600 -> "${diff / 60}м"
                diff < 86400 -> "${diff / 3600}ч"
                diff < 2_592_000 -> "${diff / 86400}д"
                diff < 31_536_000 -> "${diff / 2_592_000}мес"
                else -> "${diff / 31_536_000}г"
            }
        } catch (_: Exception) {
            gmtDate
        }
    }
}
