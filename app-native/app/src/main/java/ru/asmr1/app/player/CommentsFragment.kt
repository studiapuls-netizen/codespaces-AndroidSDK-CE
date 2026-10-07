package ru.asmr1.app.player

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
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

class CommentsFragment : Fragment() {

    companion object {
        const val ARG_VIDEO = "video"
        private const val PER_PAGE = 20

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
    private var totalComments = 0

    // Ответ на комментарий (если задан — отправим как ответ)
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

        loadComments(reset = true)
    }

    // ============================================================
    // RECYCLER
    // ============================================================

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

    // ============================================================
    // СОРТИРОВКА
    // ============================================================

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

    // ============================================================
    // ПОЛЕ ВВОДА
    // ============================================================

    private fun setupInput() {
        binding.commentSendBtn.setOnClickListener { sendComment() }

        // Многострочный: Enter = перенос, кнопка = отправка
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
        val parentId = replyToComment?.id ?: 0

        sendJob?.cancel()
        sendJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.commentSendBtn.isEnabled = false
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).postComment(
                    v.id,
                    CommentCreateRequest(content = text, parentId = parentId)
                )

                if (_binding == null) return@launch

                if (response.success && response.comment != null) {
                    binding.commentInput.text?.clear()
                    replyToComment = null
                    binding.commentInput.hint = "Написать комментарий..."

                    // Перезагружаем список с первой страницы — новый комментарий будет виден
                    loadComments(reset = true)

                    Toast.makeText(requireContext(), "Комментарий отправлен", Toast.LENGTH_SHORT).show()

                    // Прячем клавиатуру
                    val imm = ContextCompat.getSystemService(
                        requireContext(),
                        InputMethodManager::class.java
                    )
                    imm?.hideSoftInputFromWindow(binding.commentInput.windowToken, 0)
                } else {
                    Toast.makeText(
                        requireContext(),
                        response.message ?: "Не удалось отправить",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(requireContext(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            } finally {
                if (_binding != null) {
                    binding.commentSendBtn.isEnabled = true
                }
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

    // ============================================================
    // ЗАГРУЗКА КОММЕНТАРИЕВ
    // ============================================================

    private fun loadComments(reset: Boolean) {
        if (isLoading) return
        if (!hasMore && !reset) return

        val v = video ?: return
        if (reset) {
            currentPage = 1
            hasMore = true
            adapter.setComments(emptyList())
            showSkeleton(true)
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
                    totalComments = response.total
                    if (reset) {
                        adapter.setComments(response.comments)
                        binding.commentsEmpty.visibility =
                            if (response.comments.isEmpty()) View.VISIBLE else View.GONE
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
                    showSkeleton(false)
                    binding.commentsLoadingMore.visibility = View.GONE
                }
            }
        }
    }

    private fun showSkeleton(show: Boolean) {
        if (_binding == null) return
        // Простой скелетон: прячем recycler, показываем пустой экран-заглушку
        // (можно расширить: делать shimmer, но это опционально)
        binding.commentsEmpty.visibility = if (show && adapter.itemCount == 0) View.GONE else binding.commentsEmpty.visibility
    }

    // ============================================================
    // ЛАЙК
    // ============================================================

    private fun onLikeClick(comment: Comment) {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите, чтобы лайкать", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).toggleCommentLike(comment.id)
                if (_binding == null) return@launch

                if (response.success) {
                    val updated = comment.copy(
                        likes = response.likes,
                        liked = response.liked
                    )
                    adapter.updateComment(updated)
                } else {
                    Toast.makeText(
                        requireContext(),
                        response.message ?: "Ошибка",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================================
    // ПОКАЗ ОТВЕТОВ (модалка)
    // ============================================================

    private fun onShowRepliesClick(comment: Comment) {
        // Простая реализация: загружаем через API и показываем в BottomSheetDialog
        val v = video ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken()
                val api = if (token != null) {
                    ApiClient.createJwtService(token)
                } else {
                    ApiClient.service
                }
                // Переиспользуем getComments — но это корневые.
                // Правильнее иметь отдельный эндпоинт /comments/{parent_id}/replies
                // Сейчас — просто откроем шторку с текстом "скоро".
                showRepliesSheet(comment)
            } catch (_: Exception) {
                showRepliesSheet(comment)
            }
        }
    }

    private fun showRepliesSheet(parent: Comment) {
        try {
            val dialog = BottomSheetDialog(requireContext())
            val view = layoutInflater.inflate(R.layout.fragment_comments, null)
            // Не заморачиваемся пока: показываем заголовок + список уже загруженных первых 3
            val tv = android.widget.TextView(requireContext()).apply {
                text = "Ответы на комментарий «${parent.content.take(40)}...»\n\nВсего: ${parent.repliesCount}\n\n(полная загрузка будет в следующей итерации)"
                setPadding(48, 48, 48, 48)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
            dialog.setContentView(tv)
            dialog.show()
        } catch (_: Exception) {}
    }

    // ============================================================
    // ЗАКРЫТИЕ
    // ============================================================

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
