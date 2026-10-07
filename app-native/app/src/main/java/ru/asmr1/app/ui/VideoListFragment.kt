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
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentVideoListBinding

class VideoListFragment : Fragment() {

    companion object {
        const val ARG_TAB = "tab"
        val sharedPool: RecyclerView.RecycledViewPool by lazy { RecyclerView.RecycledViewPool() }
        fun newInstance(tab: String) = VideoListFragment().apply {
            arguments = Bundle().apply { putString(ARG_TAB, tab) }
        }
    }

    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VideoAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var parentPager: ViewPager2? = null

    private var currentTab = "recommended"
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val perPage = 10
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentTab = arguments?.getString(ARG_TAB) ?: "recommended"
        parentPager = findParentViewPager2(view)

        setupRecyclerView()
        setupSwipeRefresh()
        setupInfiniteScroll()
        loadFeed(1, false)
    }

    private fun findParentViewPager2(view: View): ViewPager2? {
        var v: View? = view.parent as? View
        while (v != null) {
            if (v is ViewPager2) return v
            v = v.parent as? View
        }
        return null
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter(
            onClick = { video ->
                (requireActivity() as ru.asmr1.app.MainActivity).openPlayer(video)
            },
            onMoreClick = { video, _ -> showVideoMenu(video) }
        )
        layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.setItemViewCacheSize(30)
        binding.recyclerView.setRecycledViewPool(sharedPool)
        binding.recyclerView.isNestedScrollingEnabled = true
        layoutManager.isItemPrefetchEnabled = true
        layoutManager.initialPrefetchItemCount = 10

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING,
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        parentPager?.isUserInputEnabled = false
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        parentPager?.isUserInputEnabled = true
                    }
                }
            }
        })
    }

    fun scrollToTop() {
        _binding?.recyclerView?.scrollToPosition(0)
    }

    private fun showVideoMenu(video: Video) {
        try {
            val sheetView = layoutInflater.inflate(R.layout.sheet_video_menu, null)
            val dialog = BottomSheetDialog(requireContext())
            dialog.setContentView(sheetView)
            sheetView.findViewById<View>(R.id.menu_hide)?.setOnClickListener { dialog.dismiss() }
            sheetView.findViewById<View>(R.id.menu_hide_channel)?.setOnClickListener { dialog.dismiss() }
            sheetView.findViewById<View>(R.id.menu_save)?.setOnClickListener { dialog.dismiss() }
            sheetView.findViewById<View>(R.id.menu_report)?.setOnClickListener { dialog.dismiss() }
            sheetView.findViewById<View>(R.id.menu_cancel)?.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) {
            Log.e("VideoListFragment", "showVideoMenu failed", e)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            hasMore = true
            loadFeed(1, false)
        }
    }

    private fun setupInfiniteScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val last = layoutManager.findLastVisibleItemPosition()
                if (last > 0) adapter.prefetchAround(recyclerView.context, last)
                if (dy <= 0) return
                if (isLoading || !hasMore) return
                if (_binding == null) return

                val totalItems = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (totalItems > 0 && lastVisible >= totalItems - 10) {
                    currentPage++
                    loadFeed(currentPage, true)
                }
            }
        })
    }

    private fun loadFeed(page: Int, append: Boolean) {
        if (isLoading) return
        isLoading = true
        if (!append) {
            if (_binding != null) {
                if (adapter.itemCount == 0) adapter.showSkeleton()
                else binding.swipeRefresh.isRefreshing = true
            }
        }
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val response = ApiClient.service.getFeed(tab = currentTab, page = page, perPage = perPage)
                if (_binding == null) return@launch
                if (response.success) {
                    if (append) adapter.appendVideos(response.videos)
                    else adapter.setVideos(response.videos)
                    hasMore = response.hasMore
                } else {
                    adapter.setVideos(emptyList())
                    hasMore = false
                }
            } catch (e: Exception) {
                Log.e("VideoListFragment", "Ошибка загрузки", e)
                if (_binding != null) adapter.setVideos(emptyList())
                hasMore = false
            } finally {
                isLoading = false
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        parentPager?.isUserInputEnabled = true
        _binding = null
    }
}
