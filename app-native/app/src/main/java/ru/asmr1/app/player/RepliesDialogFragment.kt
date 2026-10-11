package ru.asmr1.app.player

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.Comment
import ru.asmr1.app.data.CommentCreateRequest
import ru.asmr1.app.databinding.DialogRepliesBinding
import kotlin.math.abs

class RepliesDialogFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_PARENT = "parent_comment"
        private const val ARG_VIDEO_ID = "video_id"
        private const val DRAG_THRESHOLD = 150f

        fun newInstance(parent: Comment, videoId: Int) = RepliesDialogFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_PARENT, parent)
                putInt(ARG_VIDEO_ID, videoId)
            }
        }
    }

    private var _binding: DialogRepliesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CommentsAdapter
    private lateinit var authManager: AuthManager

    private var parentComment: Comment? = null
    private var videoId: Int = 0

    private var sheet: View? = null
    private var behavior: BottomSheetBehavior<View>? = null

    private var dragStartY = 0f
    private var dragStartTranslation = 0f
    private var isDragging = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRepliesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        parentComment = requireArguments().getParcelable(ARG_PARENT)
        videoId = requireArguments().getInt(ARG_VIDEO_ID, 0)

        val parent = parentComment ?: run {
            dismiss()
            return
        }

        setupParentView(parent)
        setupRecycler()
        setupInput()
        setupClose()

        loadReplies(parent.id)
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog ?: return
        val sheetView = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        sheet = sheetView

        val screenHeight = resources.displayMetrics.heightPixels
        val targetHeight = (screenHeight * 0.85).toInt()

        sheetView.layoutParams = sheetView.layoutParams.apply {
            height = targetHeight
        }

        behavior = BottomSheetBehavior.from(sheetView).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            isDraggable = false
            isHideable = true
            skipCollapsed = true
            peekHeight = targetHeight
        }

        setupHeaderDrag()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHeaderDrag() {
        val header = binding.repliesHeader
        val sheetView = sheet ?: return

        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    dragStartTranslation = sheetView.translationY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - dragStartY
                    if (!isDragging && abs(dy) > 10f) isDragging = true
                    if (isDragging) {
                        val newTranslation = (dragStartTranslation + dy).coerceAtLeast(0f)
                        sheetView.translationY = newTranslation
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val dy = event.rawY - dragStartY
                    if (isDragging && dy > DRAG_THRESHOLD) {
                        dismiss()
                    } else {
                        sheetView.animate()
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

    private fun setupParentView(parent: Comment) {
        val parentBinding = ru.asmr1.app.databinding.ItemCommentBinding.inflate(
            layoutInflater, binding.parentContainer, false
        )

        parentBinding.commentAuthor.text = parent.authorName
        parentBinding.commentDate.text = ""
        parentBinding.commentText.text = parent.content
        parentBinding.commentLikeCount.text = parent.likes.toString()

        if (parent.authorAvatar.isNotEmpty()) {
            parentBinding.commentAvatar.load(parent.authorAvatar) {
                placeholder(R.color.card_bg)
                error(R.color.card_bg)
            }
        }

        parentBinding.commentShowRepliesRow.visibility = View.GONE
        parentBinding.commentReplyBtn.visibility = View.GONE

        binding.parentContainer.removeAllViews()
        binding.parentContainer.addView(parentBinding.root)
    }

    private fun setupRecycler() {
        adapter = CommentsAdapter(
            onLikeClick = { comment -> onLikeClick(comment) },
            onReplyClick = { comment ->
                binding.replyInput.hint = "Ответить ${comment.authorName}..."
                binding.replyInput.requestFocus()
                val imm = ContextCompat.getSystemService(
                    requireContext(),
                    InputMethodManager::class.java
                )
                imm?.showSoftInput(binding.replyInput, InputMethodManager.SHOW_IMPLICIT)
            },
            onShowRepliesClick = { }
        )
        binding.repliesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.repliesRecycler.adapter = adapter
        binding.repliesRecycler.setHasFixedSize(false)
        binding.repliesRecycler.itemAnimator = null

        // Свайп вправо = Ответить
        binding.repliesRecycler.addOnItemTouchListener(
            SwipeReplyHelper(binding.repliesRecycler) { position ->
                val comment = adapter.getCommentAt(position)
                if (comment != null) {
                    binding.replyInput.hint = "Ответить ${comment.authorName}..."
                    binding.replyInput.requestFocus()
                    val imm = ContextCompat.getSystemService(
                        requireContext(),
                        InputMethodManager::class.java
                    )
                    imm?.showSoftInput(binding.replyInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        )
    }

    private fun setupInput() {
        binding.replySendBtn.setOnClickListener { sendReply() }
        binding.replyInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val has = !s.isNullOrBlank()
                binding.replySendBtn.isEnabled = has
                binding.replySendBtn.alpha = if (has) 1f else 0.5f
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.replySendBtn.isEnabled = false
        binding.replySendBtn.alpha = 0.5f
    }

    private fun setupClose() {
        binding.repliesClose.setOnClickListener { dismiss() }
    }

    private fun loadReplies(parentId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken()
                val api = if (token != null) {
                    ApiClient.createJwtService(token)
                } else {
                    ApiClient.service
                }
                val response = api.getCommentReplies(parentId, page = 1, perPage = 100)
                if (_binding == null) return@launch

                if (response.success) {
                    adapter.setComments(response.replies)
                    binding.repliesEmpty.visibility =
                        if (response.replies.isEmpty()) View.VISIBLE else View.GONE
                    binding.repliesRecycler.visibility =
                        if (response.replies.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    binding.repliesEmpty.visibility = View.VISIBLE
                    binding.repliesRecycler.visibility = View.GONE
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.repliesEmpty.visibility = View.VISIBLE
                binding.repliesRecycler.visibility = View.GONE
                Toast.makeText(requireContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendReply() {
        val text = binding.replyInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите, чтобы отвечать", Toast.LENGTH_SHORT).show()
            return
        }

        val parent = parentComment ?: return

        val draft = Comment(
            id = -System.currentTimeMillis().toInt(),
            content = text,
            authorId = authManager.getUserId(),
            authorName = authManager.getDisplayName() ?: "Вы",
            authorAvatar = "",
            date = "", dateGmt = "",
            likes = 0, liked = false,
            parentId = parent.id,
            repliesCount = 0, replies = emptyList()
        )
        adapter.setComments(adapter.getComments() + draft)
        binding.repliesEmpty.visibility = View.GONE
        binding.repliesRecycler.visibility = View.VISIBLE
        binding.replyInput.text?.clear()
        binding.replySendBtn.isEnabled = false
        binding.replySendBtn.alpha = 0.5f

        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.replyInput.windowToken, 0)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run {
                    removeDraft(draft)
                    return@launch
                }
                val response = ApiClient.createJwtService(token).postComment(
                    videoId,
                    CommentCreateRequest(content = text, parentId = parent.id)
                )
                if (_binding == null) return@launch

                if (response.success && response.comment != null) {
                    val newList = adapter.getComments().map {
                        if (it.id == draft.id) response.comment else it
                    }
                    adapter.setComments(newList)
                } else {
                    removeDraft(draft)
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                removeDraft(draft)
                Toast.makeText(requireContext(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeDraft(draft: Comment) {
        val newList = adapter.getComments().filterNot { it.id == draft.id }
        adapter.setComments(newList)
        if (newList.isEmpty()) {
            binding.repliesEmpty.visibility = View.VISIBLE
            binding.repliesRecycler.visibility = View.GONE
        }
    }

    private fun onLikeClick(comment: Comment) {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите, чтобы лайкать", Toast.LENGTH_SHORT).show()
            return
        }

        val optimistic = comment.copy(
            liked = !comment.liked,
            likes = if (comment.liked) (comment.likes - 1).coerceAtLeast(0) else comment.likes + 1
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
                    adapter.updateComment(comment.copy(likes = response.likes, liked = response.liked))
                } else {
                    adapter.updateComment(comment)
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                adapter.updateComment(comment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        sheet = null
        behavior = null
    }
}
