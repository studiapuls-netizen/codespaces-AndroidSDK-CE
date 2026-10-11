package ru.asmr1.app.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import ru.asmr1.app.data.ProfileVideo
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private lateinit var adapter: HistoryAdapter

    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val perPage = 20

    private var loadJob: Job? = null
    private var removeJob: Job? = null
    private var clearJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnClearHistory.setOnClickListener { confirmClearHistory() }

        setupRecycler()
        setupSwipeRefresh()
        loadHistory(reset = true)
    }

    private fun setupRecycler() {
        adapter = HistoryAdapter(
            onClick = { pv -> openPlayer(pv) },
            onRemove = { pv -> removeFromHistory(pv) }
        )
        binding.historyRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecycler.adapter = adapter
        binding.historyRecycler.setHasFixedSize(false)

        binding.historyRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (isLoading || !hasMore) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 3 && adapter.itemCount > 0) {
                    currentPage++
                    loadHistory(reset = false)
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            hasMore = true
            loadHistory(reset = true)
        }
    }

    private fun loadHistory(reset: Boolean) {
        if (isLoading) return
        if (!hasMore && !reset) return

        if (reset) {
            currentPage = 1
            hasMore = true
            showLoading(adapter.itemCount == 0)
        }

        isLoading = true

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).getMeHistory(
                    page = currentPage,
                    perPage = perPage
                )

                if (_binding == null) return@launch

                if (response.success) {
                    val items = if (reset) {
                        adapter.setItems(response.items)
                        response.items
                    } else {
                        val combined = adapter.getItems() + response.items
                        adapter.setItems(combined)
                        combined
                    }
                    updateCount(response.total)
                    hasMore = response.hasMore
                    showEmpty(items.isEmpty())
                } else {
                    hasMore = false
                    if (reset) showEmpty(true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null && reset) {
                    showEmpty(true)
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                hasMore = false
            } finally {
                if (_binding != null) {
                    isLoading = false
                    showLoading(false)
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun removeFromHistory(video: ProfileVideo) {
        val previous = adapter.getItems()
        val newList = previous.filterNot { it.id == video.id }
        adapter.setItems(newList)
        updateCount(newList.size)
        if (newList.isEmpty()) showEmpty(true)

        removeJob?.cancel()
        removeJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).removeFromHistory(video.id)

                if (_binding == null) return@launch

                if (response.success) {
                    updateCount(response.total)
                } else {
                    adapter.setItems(previous)
                    updateCount(previous.size)
                    if (previous.isNotEmpty()) showEmpty(false)
                    Toast.makeText(requireContext(), response.message ?: "Не удалось удалить", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    adapter.setItems(previous)
                    updateCount(previous.size)
                    if (previous.isNotEmpty()) showEmpty(false)
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить историю?")
            .setMessage("Вся история просмотров будет удалена. Это действие нельзя отменить.")
            .setPositiveButton("Очистить") { _, _ -> clearHistory() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearHistory() {
        clearJob?.cancel()
        clearJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).clearHistory()

                if (_binding == null) return@launch

                if (response.success) {
                    adapter.setItems(emptyList())
                    updateCount(0)
                    showEmpty(true)
                    Toast.makeText(requireContext(), "История очищена", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateCount(count: Int) {
        _binding?.historyCount?.text = if (count > 0) "$count видео" else ""
    }

    private fun showLoading(show: Boolean) {
        _binding?.historyLoading?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(show: Boolean) {
        _binding?.historyEmpty?.visibility = if (show) View.VISIBLE else View.GONE
        _binding?.swipeRefresh?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun openPlayer(pv: ProfileVideo) {
        val video = Video(
            id = pv.id,
            title = pv.title,
            description = "",
            thumbnail = pv.thumbnail,
            videoUrl = pv.videoUrl ?: "",
            audioUrl = pv.audioUrl,
            duration = pv.duration,
            views = pv.views,
            likes = 0,
            published = "",
            author = Author(id = pv.authorId, name = pv.authorName, avatar = ""),
            tags = null
        )
        (activity as? MainActivity)?.openPlayer(video)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        removeJob?.cancel()
        clearJob?.cancel()
        _binding = null
    }
}
