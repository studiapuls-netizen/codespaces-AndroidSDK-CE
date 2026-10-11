package ru.asmr1.app.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import ru.asmr1.app.R
import ru.asmr1.app.data.Comment
import ru.asmr1.app.databinding.ItemCommentSliderBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CommentsSliderAdapter(
    private var items: List<Comment> = emptyList(),
    private val onClick: (Comment) -> Unit = {}
) : RecyclerView.Adapter<CommentsSliderAdapter.SliderViewHolder>() {

    companion object {
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun setItems(newItems: List<Comment>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderViewHolder {
        val binding = ItemCommentSliderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SliderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class SliderViewHolder(private val binding: ItemCommentSliderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            binding.sliderAuthor.text = comment.authorName
            binding.sliderDate.text = formatDate(comment.dateGmt)
            binding.sliderText.text = comment.content

            if (comment.authorAvatar.isNotEmpty()) {
                binding.sliderAvatar.load(comment.authorAvatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                }
            }

            binding.root.setOnClickListener { onClick(comment) }
        }
    }

    private fun formatDate(gmtDate: String): String {
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
