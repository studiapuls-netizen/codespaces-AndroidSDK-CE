package ru.asmr1.app.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import ru.asmr1.app.R
import ru.asmr1.app.data.Comment
import ru.asmr1.app.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CommentsAdapter(
    private var comments: List<Comment> = emptyList(),
    private val onLikeClick: (Comment) -> Unit = {},
    private val onReplyClick: (Comment) -> Unit = {},
    private val onShowRepliesClick: (Comment) -> Unit = {}
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    companion object {
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun setComments(newComments: List<Comment>) {
        val old = comments
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newComments.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newComments[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newComments[n]
        })
        comments = newComments
        diff.dispatchUpdatesTo(this)
    }

    fun appendComments(newComments: List<Comment>) {
        if (newComments.isEmpty()) return
        val startPos = comments.size
        comments = comments + newComments
        notifyItemRangeInserted(startPos, newComments.size)
    }

    fun updateComment(updated: Comment) {
        val idx = comments.indexOfFirst { it.id == updated.id }
        if (idx < 0) return
        val newList = comments.toMutableList()
        newList[idx] = updated
        comments = newList
        notifyItemChanged(idx)
    }

    fun getComments(): List<Comment> = comments

    fun getCommentAt(position: Int): Comment? = comments.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(private val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            binding.commentAuthor.text = comment.authorName
            binding.commentDate.text = formatDate(comment.dateGmt)
            binding.commentText.text = comment.content

            if (comment.authorAvatar.isNotEmpty()) {
                binding.commentAvatar.load(comment.authorAvatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    allowHardware(true)
                }
            }

            binding.commentLikeCount.text = if (comment.likes > 0) comment.likes.toString() else "0"
            binding.commentLikeIcon.setImageResource(
                if (comment.liked) R.drawable.ic_player_like_filled else R.drawable.ic_player_like
            )
            if (comment.liked) {
                ImageViewCompat.setImageTintList(
                    binding.commentLikeIcon,
                    ContextCompat.getColorStateList(binding.root.context, R.color.accent_red)
                )
                binding.commentLikeCount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.accent_red)
                )
            } else {
                ImageViewCompat.setImageTintList(
                    binding.commentLikeIcon,
                    ContextCompat.getColorStateList(binding.root.context, R.color.text_secondary)
                )
                binding.commentLikeCount.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.text_secondary)
                )
            }

            binding.commentLikeBtn.setOnClickListener { onLikeClick(comment) }
            binding.commentReplyBtn.setOnClickListener { onReplyClick(comment) }

            if (comment.repliesCount > 0) {
                binding.commentShowRepliesRow.visibility = View.VISIBLE
                binding.commentShowReplies.text = "Показать ответы (${comment.repliesCount})"
                binding.commentShowReplies.setOnClickListener { onShowRepliesClick(comment) }
            } else {
                binding.commentShowRepliesRow.visibility = View.GONE
            }

            binding.commentCard.translationX = 0f
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
