package ru.asmr1.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.databinding.ItemDownloadBinding
import ru.asmr1.app.download.DownloadedFile
import java.util.Locale
import java.util.concurrent.TimeUnit

class DownloadsAdapter(
    private var items: List<DownloadedFile> = emptyList(),
    private val onClick: (DownloadedFile) -> Unit = {},
    private val onDelete: (DownloadedFile) -> Unit = {}
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

    fun setItems(newItems: List<DownloadedFile>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) =
                old[o].videoId == newItems[n].videoId && old[o].type == newItems[n].type
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newItems[n]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadedFile) {
            binding.title.text = item.title
            binding.authorName.text = item.authorName

            // Тип: видео или аудио
            binding.typeIcon.setImageResource(
                if (item.type == DownloadedFile.Type.VIDEO) R.drawable.ic_download_video
                else R.drawable.ic_download_audio
            )

            // Размер
            binding.sizeText.text = formatSize(item.size)

            // Дата скачивания
            binding.downloadDate.text = "Скачано ${timeAgo(item.downloadedAt)}"

            // Превью
            if (item.thumbnail.isNotEmpty()) {
                binding.thumbnail.load(item.thumbnail) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    allowHardware(true)
                }
            }

            // Аватар автора
            if (item.authorAvatar.isNotEmpty()) {
                binding.authorAvatar.load(item.authorAvatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                }
            }

            binding.root.setOnClickListener { onClick(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 МБ"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f ГБ", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.0f МБ", mb)
            else -> String.format(Locale.US, "%.0f КБ", kb)
        }
    }

    private fun timeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes мин назад"
            hours < 24 -> "$hours ч назад"
            days < 30 -> "$days дн назад"
            else -> "${days / 30} мес назад"
        }
    }
}
