package ru.asmr1.app.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.Comment
import ru.asmr1.app.data.CommentCreateRequest
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentCommentsBinding
import kotlin.math.abs

class CommentsFragment : Fragment() {

    companion object {
        const val ARG_VIDEO = "video"
        private const val PER_PAGE = 20
        private const val DRAG_THRESHOLD = 150f

        fun newInstance(video: Video) = CommentsFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_VIDEO, video) }
        }
    }

    private var _binding: FragmentCommentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CommentsAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var authManager: AuthManager

    private var video: Video? = null
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private var order: String = "new"

    private var replyToComment: Comment? = null

    private var loadJob: Job? = null
    private var sendJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        video = requireArguments().getParcelable(ARG_VIDEO) ?: return

        setupRecycler()
        setupSortButtons()
        setupInput()
        setupClose()
        setupDragHandle()

        loadComments(reset = true)
    }

    private fun applyPendingFocus() {
        val activity = activity as? MainActivity ?: return
        val focus = activity.pendingCommentFocus ?: return
        activity.pendingCommentFocus = null

        val comments = adapter.getComments()
        val idx = comments.indexOfFirst { it.id == focus.id }
        if (idx >= 0) {
            binding.commentsRecycler.postDelayed({
                binding.commentsRecycler.smoothScrollToPosition(idx)
            }, 150)
        }

        binding.commentInput.postDelayed({
            onReplyClick(focus)
        }, 250)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandle() {
        val container = (requireActivity() as MainActivity).findViewById<View>(
            R.id.comments_overlay_container
        ) ?: return

        var startY = 0f
        var startTranslation = 0f
        var isDragging = false

        binding.handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startTranslation = container.translationY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (!isDragging && abs(dy) > 10f) isDragging = true
                    if (isDragging) {
                        val newTranslation = (startTranslation + dy).coerceAtLeast(0f)
                        container.translationY = newTranslation
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val dy = event.rawY - startY
                    if (isDragging && dy > DRAG_THRESHOLD) {
                        (requireActivity() as MainActivity).closeComments()
                    } else {
                        container.animate()
                            .translationY(0f)
                            .setDuration(200)
                            .start()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecycler() {
        adapter = CommentsAdapter(
            onLikeClick = { comment -> onLikeClick(comment) },
            onReplyClick = { comment -> onReplyClick(comment) },
            onShowRepliesClick = { comment -> onShowRepliesClick(comment) }
        )
        layoutManager = LinearLayoutManager(requireContext())
        binding.commentsRecycler.layoutManager = layoutManager
        binding.commentsRecycler.adapter = adapter
        binding.commentsRecycler.setHasFixedSize(false)
        binding.commentsRecycler.itemAnimator = null

        // Свайп вправо = Ответить
        binding.commentsRecycler.addOnItemTouchListener(
            SwipeReplyHelper(binding.commentsRecycler) { position ->
                val comment = adapter.getCommentAt(position)
                if (comment != null) onReplyClick(comment)
            }
        )

        binding.commentsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return
                if (isLoading || !hasMore) return
                if (_binding == null) return

                val total = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (total > 0 && lastVisible >= total - 3) {
                    currentPage++
                    loadComments(reset = false)
                }
            }
        })
    }

    private fun setupSortButtons() {
        updateSortButtons()

        binding.sortNew.setOnClickListener {
            if (order == "new") return@setOnClickListener
            order = "new"
            updateSortButtons()
            loadComments(reset = true)
        }

        binding.sortTop.setOnClickListener {
            if (order == "top") return@setOnClickListener
            order = "top"
            updateSortButtons()
            loadComments(reset = true)
        }
    }

    private fun updateSortButtons() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.sortNew.setTextColor(if (order == "new") activeColor else inactiveColor)
        binding.sortTop.setTextColor(if (order == "top") activeColor else inactiveColor)
    }

    private fun setupInput() {
        binding.commentSendBtn.setOnClickListener { sendComment() }

        binding.commentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val has = !s.isNullOrBlank()
                binding.commentSendBtn.isEnabled = has
                binding.commentSendBtn.alpha = if (has) 1f else 0.5f
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.commentSendBtn.isEnabled = false
        binding.commentSendBtn.alpha = 0.5f
    }

    private fun sendComment() {
        val text = binding.commentInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите, чтобы комментировать", Toast.LENGTH_SHORT).show()
            return
        }

        val v = video ?: return
        val parent = replyToComment
        val parentId = parent?.id ?: 0

        val draft = Comment(
            id = -System.currentTimeMillis().toInt(),
            content = text,
            authorId = authManager.getUserId(),
            authorName = authManager.getDisplayName() ?: "Вы",
            authorAvatar = "",
            date = "",
            dateGmt = "",
            likes = 0,
            liked = false,
            parentId = parentId,
            repliesCount = 0,
            replies = emptyList()
        )

        if (parent == null) {
            adapter.setComments(listOf(draft) + adapter.getComments())
        } else {
            val parentInList = adapter.getComments().firstOrNull { it.id == parent.id }
            if (parentInList != null) {
                val updatedParent = parentInList.copy(
                    replies = parentInList.replies + draft,
                    repliesCount = parentInList.repliesCount + 1
                )
                adapter.updateComment(updatedParent)
            }
        }

        binding.commentInput.text?.clear()
        replyToComment = null
        binding.commentInput.hint = "Написать комментарий..."
        binding.commentSendBtn.isEnabled = false
        binding.commentSendBtn.alpha = 0.5f

        val imm = ContextCompat.getSystemService(
            requireContext(),
            InputMethodManager::class.java
        )
        imm?.hideSoftInputFromWindow(binding.commentInput.windowToken, 0)

        sendJob?.cancel()
        sendJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run {
                    removeDraft(draft)
                    return@launch
                }
                val response = ApiClient.createJwtService(token).postComment(
                    v.id,
                    CommentCreateRequest(content = text, parentId = parentId)
                )

                if (_binding == null) return@launch

                if (response.success && response.comment != null) {
                    replaceDraft(draft, response.comment)
                } else {
                    removeDraft(draft)
                    Toast.makeText(
                        requireContext(),
                        response.message ?: "Не удалось отправить",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                removeDraft(draft)
                Toast.makeText(
                    requireContext(),
                    "Ошибка: ${e.javaClass.simpleName}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun removeDraft(draft: Comment) {
        if (draft.parentId == 0) {
            val newList = adapter.getComments().filterNot { it.id == draft.id }
            adapter.setComments(newList)
        } else {
            val parentInList = adapter.getComments().firstOrNull { it.id == draft.parentId }
            if (parentInList != null) {
                val updatedParent = parentInList.copy(
                    replies = parentInList.replies.filterNot { it.id == draft.id },
                    repliesCount = (parentInList.repliesCount - 1).coerceAtLeast(0)
                )
                adapter.updateComment(updatedParent)
            }
        }
    }

    private fun replaceDraft(draft: Comment, real: Comment) {
        if (draft.parentId == 0) {
            val idx = adapter.getComments().indexOfFirst { it.id == draft.id }
            if (idx >= 0) {
                val newList = adapter.getComments().toMutableList()
                newList[idx] = real
                adapter.setComments(newList)
            }
        } else {
            val parentInList = adapter.getComments().firstOrNull { it.id == draft.parentId }
            if (parentInList != null) {
                val updatedReplies = parentInList.replies.map {
                    if (it.id == draft.id) real else it
                }
                val updatedParent = parentInList.copy(
                    replies = updatedReplies,
                    repliesCount = updatedReplies.size
                )
                adapter.updateComment(updatedParent)
            }
        }
    }

    private fun onReplyClick(comment: Comment) {
        replyToComment = comment
        binding.commentInput.hint = "Ответить ${comment.authorName}..."
        binding.commentInput.requestFocus()
        binding.commentInput.setSelection(binding.commentInput.text?.length ?: 0)

        val imm = ContextCompat.getSystemService(
            requireContext(),
            InputMethodManager::class.java
        )
        imm?.showSoftInput(binding.commentInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun loadComments(reset: Boolean) {
        if (isLoading) return
        if (!hasMore && !reset) return

        val v = video ?: return
        if (reset) {
            currentPage = 1
            hasMore = true
            adapter.setComments(emptyList())
        } else {
            binding.commentsLoadingMore.visibility = View.VISIBLE
        }
        isLoading = true

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken()
                val api = if (token != null) {
                    ApiClient.createJwtService(token)
                } else {
                    ApiClient.service
                }
                val response = api.getComments(
                    videoId = v.id,
                    page = currentPage,
                    perPage = PER_PAGE,
                    order = order
                )

                if (_binding == null) return@launch

                if (response.success) {
                    if (reset) {
                        adapter.setComments(response.comments)
                        binding.commentsEmpty.visibility =
                            if (response.comments.isEmpty()) View.VISIBLE else View.GONE
                        applyPendingFocus()
                    } else {
                        adapter.appendComments(response.comments)
                    }
                    hasMore = response.hasMore
                } else {
                    if (reset) {
                        binding.commentsEmpty.visibility = View.VISIBLE
                    }
                    hasMore = false
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                if (reset) {
                    binding.commentsEmpty.visibility = View.VISIBLE
                }
                hasMore = false
            } finally {
                if (_binding != null) {
                    isLoading = false
                    binding.commentsLoadingMore.visibility = View.GONE
                }
            }
        }
    }

    private fun onLikeClick(comment: Comment) {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите, чтобы лайкать", Toast.LENGTH_SHORT).show()
            return
        }

        val wasLiked = comment.liked
        val optimistic = comment.copy(
            liked = !wasLiked,
            likes = if (wasLiked) (comment.likes - 1).coerceAtLeast(0) else comment.likes + 1
        )
        adapter.updateComment(optimistic)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run {
                    adapter.updateComment(comment)
                    return@launch
                }
                val response = ApiClient.createJwtService(token).toggleCommentLike(comment.id)
                if (_binding == null) return@launch

                if (response.success) {
                    val finalComment = comment.copy(
                        likes = response.likes,
                        liked = response.liked
                    )
                    adapter.updateComment(finalComment)
                } else {
                    adapter.updateComment(comment)
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                adapter.updateComment(comment)
            }
        }
    }

    private fun onShowRepliesClick(comment: Comment) {
        val v = video ?: return
        val dialog = RepliesDialogFragment.newInstance(comment, v.id)
        dialog.show(childFragmentManager, "replies_dialog")
    }

    private fun setupClose() {
        binding.btnCommentsClose.setOnClickListener {
            (requireActivity() as MainActivity).closeComments()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        sendJob?.cancel()
        _binding = null
    }
}
