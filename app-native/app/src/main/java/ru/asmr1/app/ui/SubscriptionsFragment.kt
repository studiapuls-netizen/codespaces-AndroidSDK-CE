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
import ru.asmr1.app.databinding.FragmentSubscriptionsBinding

class SubscriptionsFragment : Fragment() {

    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var authorsAdapter: SubscriptionsAuthorsAdapter
    private lateinit var videosAdapter: VideoAdapter
    private lateinit var videosLayoutManager: LinearLayoutManager
    private lateinit var authManager: AuthManager

    private var selectedAuthorId: Int = 0
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val perPage = 20

    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        if (!authManager.isLoggedIn()) {
            showEmptyState()
            return
        }

        setupAuthorsRecycler()
        setupVideosRecycler()
        setupSwipeRefresh()

        loadAuthors()
        loadVideos(reset = true)
    }

    private fun setupAuthorsRecycler() {
        authorsAdapter = SubscriptionsAuthorsAdapter(
            onAllClick = {
                selectedAuthorId = 0
                authorsAdapter.setSelected(0)
                loadVideos(reset = true)
            },
            onAuthorClick = { author ->
                selectedAuthorId = author.id
                authorsAdapter.setSelected(author.id)
                loadVideos(reset = true)
            }
        )
        binding.rvAuthors.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvAuthors.adapter = authorsAdapter
    }

    private fun setupVideosRecycler() {
        videosAdapter = VideoAdapter(
            onClick = { video ->
                markAuthorAsViewed(video.author.id)
                (requireActivity() as? MainActivity)?.openPlayer(video)
            },
            onMoreClick = { _, _ -> }
        )
        videosLayoutManager = LinearLayoutManager(requireContext())
        binding.rvVideos.layoutManager = videosLayoutManager
        binding.rvVideos.adapter = videosAdapter
        binding.rvVideos.setHasFixedSize(false)
        binding.rvVideos.itemAnimator = null

        binding.rvVideos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                if (isLoading || !hasMore) return
                if (_binding == null) return

                val total = videosLayoutManager.itemCount
                val lastVisible = videosLayoutManager.findLastVisibleItemPosition()
                if (total > 0 && lastVisible >= total - 5) {
                    currentPage++
                    loadVideos(reset = false)
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadAuthors()
            loadVideos(reset = true)
        }
    }

    private fun loadAuthors() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val api = ApiClient.createJwtService(token)
                val response = api.getSubscribedAuthors()

                if (_binding == null) return@launch

                if (response.success) {
                    authorsAdapter.setAuthors(response.authors)
                    if (response.authors.isNotEmpty()) {
                        binding.rvAuthors.visibility = View.VISIBLE
                        binding.llEmpty.visibility = View.GONE
                    } else {
                        binding.rvAuthors.visibility = View.GONE
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadVideos(reset: Boolean) {
        if (isLoading) return
        if (reset) {
            currentPage = 1
            hasMore = true
        }

        isLoading = true

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val api = ApiClient.createJwtService(token)
                val response = api.getSubscriptionsFeed(
                    authorId = selectedAuthorId,
                    page = currentPage,
                    perPage = perPage
                )

                if (_binding == null) return@launch

                if (response.success) {
                    if (reset) {
                        videosAdapter.setVideos(response.videos)
                        binding.rvVideos.scrollToPosition(0)
                    } else {
                        videosAdapter.appendVideos(response.videos)
                    }
                    hasMore = response.hasMore

                    if (response.videos.isEmpty() && reset) {
                        binding.llEmpty.visibility = View.VISIBLE
                        binding.swipeRefresh.visibility = View.GONE
                    } else {
                        binding.swipeRefresh.visibility = View.VISIBLE
                        binding.llEmpty.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun markAuthorAsViewed(authorId: Int) {
        authorsAdapter.markAuthorAsViewed(authorId)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val api = ApiClient.createJwtService(token)
                api.markAuthorViewed(authorId)
            } catch (_: Exception) { }
        }
    }

    private fun showEmptyState() {
        _binding?.apply {
            rvAuthors.visibility = View.GONE
            swipeRefresh.visibility = View.GONE
            llEmpty.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }
}
