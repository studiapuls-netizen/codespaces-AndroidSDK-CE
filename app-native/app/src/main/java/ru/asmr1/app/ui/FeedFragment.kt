package ru.asmr1.app.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentFeedBinding

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VideoAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var currentTab = "recommended"
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val perPage = 20

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabs()
        setupSwipeRefresh()
        setupInfiniteScroll()
        loadFeed(currentTab, 1, false)
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter { video ->
            openVideo(video)
        }
        layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabRecommended.setOnClickListener {
            if (currentTab == "recommended") return@setOnClickListener
            switchTab("recommended")
        }
        binding.tabLatest.setOnClickListener {
            if (currentTab == "latest") return@setOnClickListener
            switchTab("latest")
        }
        updateTabsUi()
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        currentPage = 1
        hasMore = true
        binding.recyclerView.scrollToPosition(0)
        updateTabsUi()
        loadFeed(tab, 1, false)
    }

    private fun updateTabsUi() {
        val active = requireContext().getColor(R.color.accent_red)
        val inactive = requireContext().getColor(R.color.card_bg)
        binding.tabRecommended.setBackgroundColor(
            if (currentTab == "recommended") active else inactive
        )
        binding.tabLatest.setBackgroundColor(
            if (currentTab == "latest") active else inactive
        )
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            hasMore = true
            loadFeed(currentTab, 1, false)
        }
    }

    private fun setupInfiniteScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                if (isLoading || !hasMore) return

                val totalItems = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (lastVisible >= totalItems - 5) {
                    currentPage++
                    loadFeed(currentTab, currentPage, true)
                }
            }
        })
    }

    private fun loadFeed(tab: String, page: Int, append: Boolean) {
        if (isLoading) return
        isLoading = true

        if (!append) {
            binding.swipeRefresh.isRefreshing = true
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getFeed(tab = tab, page = page, perPage = perPage)
                if (response.success) {
                    if (append) adapter.appendVideos(response.videos)
                    else adapter.setVideos(response.videos)
                    hasMore = response.hasMore
                } else {
                    hasMore = false
                }
            } catch (e: Exception) {
                Log.e("FeedFragment", "Ошибка загрузки", e)
                hasMore = false
            } finally {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun openVideo(video: Video) {
        Log.d("FeedFragment", "🎬 Открываем видео: ${video.id} — ${video.title}")
        (requireActivity() as ru.asmr1.app.MainActivity).openPlayer(video)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
