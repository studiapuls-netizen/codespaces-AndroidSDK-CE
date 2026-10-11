package ru.asmr1.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.asmr1.app.MainActivity
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.Author
import ru.asmr1.app.data.Comment
import ru.asmr1.app.data.NotificationItem
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentNotificationsTabBinding

class NotificationsTabFragment : Fragment() {

    companion object {
        private const val ARG_FILTER = "filter"
        private const val ARG_UNREAD = "unread_only"
        fun newInstance(filter: String, unreadOnly: Boolean) = NotificationsTabFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FILTER, filter)
                putBoolean(ARG_UNREAD, unreadOnly)
            }
        }
    }

    private var _binding: FragmentNotificationsTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationsAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var authManager: AuthManager

    private var filter: String = "all"
    private var unreadOnly: Boolean = false
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true

    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())
        filter = arguments?.getString(ARG_FILTER) ?: "all"
        unreadOnly = arguments?.getBoolean(ARG_UNREAD, false) ?: false

        setupRecycler()
        load(reset = true)
    }

    private fun setupRecycler() {
        adapter = NotificationsAdapter(
            onClick = { item -> onItemClick(item) },
            onDeleteSwipe = { item, viewToAnimate -> deleteItem(item, viewToAnimate) }
        )
        layoutManager = LinearLayoutManager(requireContext())
        binding.notifRecycler.layoutManager = layoutManager
        binding.notifRecycler.adapter = adapter
        binding.notifRecycler.itemAnimator = null

        binding.notifRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return
                if (isLoading || !hasMore) return
                if (_binding == null) return
                val total = layoutManager.itemCount
                val last = layoutManager.findLastVisibleItemPosition()
                if (total > 0 && last >= total - 3) {
                    currentPage++
                    load(reset = false)
                }
            }
        })
    }

    fun reload() {
        currentPage = 1
        hasMore = true
        adapter.setItems(emptyList())
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        if (isLoading) return
        if (!hasMore && !reset) return
        if (!authManager.isLoggedIn()) {
            binding.notifEmpty.visibility = View.VISIBLE
            return
        }

        if (reset) {
            currentPage = 1
            hasMore = true
        } else {
            binding.notifLoading.visibility = View.VISIBLE
        }
        isLoading = true

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val api = ApiClient.createJwtService(token)
                val response = api.getNotifications(
                    filter = filter,
                    page = currentPage,
                    perPage = 20
                )

                if (_binding == null) return@launch

                if (response.success) {
                    val items = if (unreadOnly) {
                        response.items.filter { !it.read }
                    } else {
                        response.items
                    }

                    if (reset) {
                        adapter.setItems(items)
                    } else {
                        adapter.appendItems(items)
                    }
                    hasMore = response.hasMore

                    binding.notifEmpty.visibility =
                        if (items.isEmpty() && reset) View.VISIBLE else View.GONE
                } else {
                    hasMore = false
                }
            } catch (_: Exception) {
                if (_binding != null && reset) binding.notifEmpty.visibility = View.VISIBLE
            } finally {
                if (_binding != null) {
                    isLoading = false
                    binding.notifLoading.visibility = View.GONE
                }
            }
        }
    }

    private fun deleteItem(item: NotificationItem, viewToAnimate: View) {
        // Сразу убираем из списка (визуально)
        val newList = adapter.getItems().filterNot { it.id == item.id }
        adapter.setItems(newList)
        if (newList.isEmpty()) binding.notifEmpty.visibility = View.VISIBLE

        // В фоне — на сервер
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                ApiClient.createJwtService(token).deleteNotification(item.id)
            } catch (_: Exception) {}
        }
    }

    private fun onItemClick(item: NotificationItem) {
        // Локально помечаем прочитанным сразу
        val updatedList = adapter.getItems().map {
            if (it.id == item.id) it.copy(read = true) else it
        }
        adapter.setItems(updatedList)

        // В фоне отправляем на сервер
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                ApiClient.createJwtService(token).markNotificationRead(item.id)
            } catch (_: Exception) {}
        }

        val activity = activity as? MainActivity ?: return

        val video = Video(
            id = item.postId,
            title = item.title,
            description = "",
            thumbnail = item.thumbnail,
            videoUrl = item.videoUrl ?: "",
            audioUrl = item.audioUrl,
            duration = 0,
            views = 0,
            likes = 0,
            published = item.timestamp,
            author = Author(
                id = item.authorId,
                name = item.authorName,
                avatar = item.authorAvatar
            ),
            tags = null
        )

        if (item.type == "reply" && item.parentCommentId > 0) {
            val stubComment = Comment(
                id = item.parentCommentId,
                content = "",
                authorId = 0,
                authorName = "",
                authorAvatar = "",
                date = "",
                dateGmt = "",
                likes = 0,
                liked = false,
                parentId = 0,
                repliesCount = 0,
                replies = emptyList()
            )
            activity.openPlayer(video)
            activity.openCommentsWithFocus(video, stubComment)
        } else {
            activity.openPlayer(video)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }
}
