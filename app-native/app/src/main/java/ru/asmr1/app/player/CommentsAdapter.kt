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
import ru.asmr1.app.utils.timeAgo
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
            // Автор
            binding.commentAuthor.text = comment.authorName

            // Время
            binding.commentDate.text = formatDate(comment.dateGmt)

            // Текст
            binding.commentText.text = comment.content

            // Аватар
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

            // Лайк
            binding.commentLikeCount.text = if (comment.likes > 0) comment.likes.toString() else "0"
            binding.commentLikeIcon.setImageResource(
                if (comment.liked) R.drawable.ic_player_like_filled else R.drawable.ic_player_like
            )
            if (comment.liked) {
                // красный
                ImageViewCompat.setImageTintList(
                    binding.commentLikeIcon,
                    ContextCompat.getColorStateList(itemView.context, R.color.accent_red)
                )
                binding.commentLikeCount.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.accent_red)
                )
            } else {
                // серый
                ImageViewCompat.setImageTintList(
                    binding.commentLikeIcon,
                    ContextCompat.getColorStateList(itemView.context, R.color.text_secondary)
                )
                binding.commentLikeCount.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_secondary)
                )
            }

            // Клик по лайку
            binding.commentLikeBtn.setOnClickListener { onLikeClick(comment) }

            // Клик по "Ответить"
            binding.commentReplyBtn.setOnClickListener { onReplyClick(comment) }

            // Показать ответы
            if (comment.repliesCount > 0) {
                binding.commentShowReplies.visibility = View.VISIBLE
                binding.commentShowReplies.text = "Показать ответы (${comment.repliesCount})"
                binding.commentShowReplies.setOnClickListener { onShowRepliesClick(comment) }
            } else {
                binding.commentShowReplies.visibility = View.GONE
            }

            // Показ первых 3 ответов (если сервер прислал)
            if (comment.replies.isNotEmpty()) {
                binding.commentRepliesContainer.visibility = View.VISIBLE
                binding.commentRepliesContainer.removeAllViews()

                val inflater = LayoutInflater.from(itemView.context)
                for (reply in comment.replies) {
                    val replyView = inflater.inflate(
                        R.layout.item_comment_reply,
                        binding.commentRepliesContainer,
                        false
                    )
                    bindReply(replyView, reply)
                    binding.commentRepliesContainer.addView(replyView)
                }
            } else {
                binding.commentRepliesContainer.visibility = View.GONE
                binding.commentRepliesContainer.removeAllViews()
            }
        }

        private fun bindReply(view: View, reply: Comment) {
            val replyAvatar = view.findViewById<android.widget.ImageView>(R.id.reply_avatar)
            val replyAuthor = view.findViewById<android.widget.TextView>(R.id.reply_author)
            val replyDate = view.findViewById<android.widget.TextView>(R.id.reply_date)
            val replyText = view.findViewById<android.widget.TextView>(R.id.reply_text)
            val replyLikeBtn = view.findViewById<View>(R.id.reply_like_btn)
            val replyLikeIcon = view.findViewById<android.widget.ImageView>(R.id.reply_like_icon)
            val replyLikeCount = view.findViewById<android.widget.TextView>(R.id.reply_like_count)

            replyAuthor.text = reply.authorName
            replyDate.text = formatDate(reply.dateGmt)
            replyText.text = reply.content

            if (reply.authorAvatar.isNotEmpty()) {
                replyAvatar.load(reply.authorAvatar) {
                    crossfade(false)
                    placeholder(R.color.card_bg)
                    error(R.color.card_bg)
                }
            }

            replyLikeCount.text = if (reply.likes > 0) reply.likes.toString() else "0"
            replyLikeIcon.setImageResource(
                if (reply.liked) R.drawable.ic_player_like_filled else R.drawable.ic_player_like
            )
            if (reply.liked) {
                ImageViewCompat.setImageTintList(
                    replyLikeIcon,
                    ContextCompat.getColorStateList(itemView.context, R.color.accent_red)
                )
                replyLikeCount.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.accent_red)
                )
            } else {
                ImageViewCompat.setImageTintList(
                    replyLikeIcon,
                    ContextCompat.getColorStateList(itemView.context, R.color.text_secondary)
                )
                replyLikeCount.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_secondary)
                )
            }

            replyLikeBtn.setOnClickListener { onLikeClick(reply) }
        }
    }

    /**
     * Парсим date_gmt (UTC) и показываем "N минут назад".
     * Если не получилось — возвращаем строку как есть.
     */
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
