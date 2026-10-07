package ru.asmr1.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.SubscribedAuthor
import ru.asmr1.app.databinding.ItemSubscribedAllBinding
import ru.asmr1.app.databinding.ItemSubscribedAuthorBinding

class SubscriptionsAuthorsAdapter(
    private val onAllClick: () -> Unit,
    private val onAuthorClick: (SubscribedAuthor) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ALL = 0
        private const val TYPE_AUTHOR = 1
    }

    private var authors: List<SubscribedAuthor> = emptyList()
    private var selectedAuthorId: Int = 0

    fun setAuthors(newAuthors: List<SubscribedAuthor>) {
        authors = newAuthors
        notifyDataSetChanged()
    }

    fun setSelected(authorId: Int) {
        selectedAuthorId = authorId
        notifyDataSetChanged()
    }

    fun markAuthorAsViewed(authorId: Int) {
        val index = authors.indexOfFirst { it.id == authorId }
        if (index < 0) return
        val current = authors[index]
        if (!current.hasNew) return
        val updated = current.copy(hasNew = false)
        authors = authors.toMutableList().apply { set(index, updated) }
        notifyItemChanged(index + 1)
    }

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_ALL else TYPE_AUTHOR

    override fun getItemCount(): Int = authors.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ALL -> AllViewHolder(
                ItemSubscribedAllBinding.inflate(inflater, parent, false)
            )
            else -> AuthorViewHolder(
                ItemSubscribedAuthorBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AllViewHolder -> holder.bind()
            is AuthorViewHolder -> holder.bind(authors[position - 1])
        }
    }

    inner class AllViewHolder(private val binding: ItemSubscribedAllBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener { onAllClick() }
            binding.root.alpha = if (selectedAuthorId == 0) 1f else 0.6f
        }
    }

    inner class AuthorViewHolder(private val binding: ItemSubscribedAuthorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(author: SubscribedAuthor) {
            binding.tvName.text = author.name

            if (author.avatar.isNotEmpty()) {
                binding.ivAvatar.load(author.avatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    allowHardware(true)
                }
            }

            binding.vNewRing.visibility = if (author.hasNew) View.VISIBLE else View.GONE

            val isSelected = selectedAuthorId == author.id
            binding.root.alpha = if (isSelected || selectedAuthorId == 0) 1f else 0.6f

            binding.root.setOnClickListener { onAuthorClick(author) }
        }
    }
}
