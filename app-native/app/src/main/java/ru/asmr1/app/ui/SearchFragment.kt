package ru.asmr1.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.databinding.FragmentSearchBinding
import ru.asmr1.app.utils.SearchHistoryManager

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SearchAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private lateinit var historyManager: SearchHistoryManager

    private var currentQuery = ""
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val perPage = 20

    // Сортировка
    private var orderBy: String = "weight"
    private var order: String = "DESC"

    private var searchJob: Job? = null
    private var searchDebounceJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyManager = SearchHistoryManager(requireContext())

        setupRecyclerView()
        setupHistoryRecycler()
        setupSearchInput()
        setupButtons()
        setupSortChips()

        showInitialState()

        binding.etSearch.postDelayed({
            if (_binding == null) return@postDelayed
            binding.etSearch.requestFocus()
            val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter { video ->
            (requireActivity() as? MainActivity)?.openPlayer(video)
        }
        layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.itemAnimator = null

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                if (isLoading || !hasMore) return
                if (_binding == null) return

                val totalItems = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (totalItems > 0 && lastVisible >= totalItems - 5) {
                    currentPage++
                    performSearch(currentQuery, currentPage, append = true)
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            if (currentQuery.isNotEmpty()) {
                currentPage = 1
                hasMore = true
                performSearch(currentQuery, 1, append = false)
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupHistoryRecycler() {
        historyAdapter = SearchHistoryAdapter(
            items = emptyList(),
            onItemClick = { query ->
                binding.etSearch.setText(query)
                binding.etSearch.setSelection(query.length)
                currentQuery = query
                currentPage = 1
                hasMore = true
                performSearch(query, 1, append = false)
                hideKeyboard()
            },
            onItemDelete = { query ->
                historyManager.removeQuery(query)
                refreshHistory()
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter
    }

    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                binding.btnClear.visibility = if (hasText) View.VISIBLE else View.GONE

                val query = s?.toString()?.trim().orEmpty()

                if (query.isEmpty()) {
                    showInitialState()
                    return
                }

                searchDebounceJob?.cancel()
                searchDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    currentQuery = query
                    currentPage = 1
                    hasMore = true
                    performSearch(query, 1, append = false)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.etSearch.text.isNullOrEmpty()) {
                showInitialState()
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchDebounceJob?.cancel()
                val query = binding.etSearch.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    historyManager.addQuery(query)
                    currentQuery = query
                    currentPage = 1
                    hasMore = true
                    performSearch(query, 1, append = false)
                    hideKeyboard()
                }
                true
            } else false
        }
    }

    private fun setupButtons() {
        binding.btnSearch.setOnClickListener {
            searchDebounceJob?.cancel()
            val query = binding.etSearch.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                historyManager.addQuery(query)
                currentQuery = query
                currentPage = 1
                hasMore = true
                performSearch(query, 1, append = false)
                hideKeyboard()
            } else {
                Toast.makeText(context, "Введите запрос", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            adapter.clear()
            showInitialState()
        }

        binding.btnClearHistory.setOnClickListener {
            historyManager.clearAll()
            showInitialState()
        }
    }

    // ===== ЧИПЫ СОРТИРОВКИ =====
    private fun setupSortChips() {
        binding.chipWeight.setOnClickListener { selectOrderBy("weight") }
        binding.chipDate.setOnClickListener { selectOrderBy("date") }
        binding.chipViews.setOnClickListener { selectOrderBy("views") }
        binding.chipLikes.setOnClickListener { selectOrderBy("likes") }
        binding.chipTitle.setOnClickListener { selectOrderBy("title") }

        binding.chipOrder.setOnClickListener {
            order = if (order == "DESC") "ASC" else "DESC"
            updateOrderChipText()
            applySortAndSearch()
        }

        updateSortChipsUI()
        updateOrderChipText()
    }

    private fun selectOrderBy(newOrderBy: String) {
        if (orderBy == newOrderBy) return
        orderBy = newOrderBy
        updateSortChipsUI()
        applySortAndSearch()
    }

    private fun updateSortChipsUI() {
        _binding?.apply {
            setChipActive(chipWeight, orderBy == "weight")
            setChipActive(chipDate, orderBy == "date")
            setChipActive(chipViews, orderBy == "views")
            setChipActive(chipLikes, orderBy == "likes")
            setChipActive(chipTitle, orderBy == "title")
        }
    }

    private fun setChipActive(chip: TextView, active: Boolean) {
        chip.isSelected = active
        chip.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) android.R.color.white else R.color.text_secondary
            )
        )
    }

    private fun updateOrderChipText() {
        _binding?.chipOrder?.text = if (order == "DESC") "↓ Убыв." else "↑ Возр."
    }

    /** Если есть активный запрос — перезапускаем поиск с новой сортировкой */
    private fun applySortAndSearch() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        if (query.isNotEmpty()) {
            currentQuery = query
            currentPage = 1
            hasMore = true
            performSearch(query, 1, append = false)
        }
    }

    private fun performSearch(query: String, page: Int, append: Boolean) {
        if (isLoading) return
        if (query.isEmpty()) return

        isLoading = true

        if (!append) {
            showSkeletons()
            _binding?.apply {
                llHistory.visibility = View.GONE
                llEmpty.visibility = View.GONE
                llNoResults.visibility = View.GONE
                swipeRefresh.visibility = View.VISIBLE
                llSortBar.visibility = View.VISIBLE
            }
        }

        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.service.search(
                    query = query,
                    page = page,
                    perPage = perPage,
                    orderBy = orderBy,
                    order = order
                )

                if (_binding == null) return@launch

                if (response.success) {
                    if (append) {
                        adapter.appendVideos(response.videos)
                    } else {
                        adapter.setVideos(response.videos)
                    }
                    hasMore = response.hasMore

                    if (response.videos.isEmpty()) {
                        showNoResults(query)
                    } else {
                        showResults()
                    }
                } else {
                    if (!append) showNoResults(query)
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(context, "Ошибка поиска: ${e.message}", Toast.LENGTH_SHORT).show()
                if (!append) showNoResults(query)
            } finally {
                isLoading = false
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun showInitialState() {
        _binding?.apply {
            swipeRefresh.visibility = View.GONE
            llNoResults.visibility = View.GONE
            llSortBar.visibility = View.GONE

            val history = historyManager.getHistory()
            if (history.isNotEmpty()) {
                historyAdapter.setItems(history)
                llHistory.visibility = View.VISIBLE
                llEmpty.visibility = View.GONE
            } else {
                llHistory.visibility = View.GONE
                llEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshHistory() {
        val history = historyManager.getHistory()
        if (history.isEmpty()) {
            _binding?.llHistory?.visibility = View.GONE
            _binding?.llEmpty?.visibility = View.VISIBLE
        } else {
            historyAdapter.setItems(history)
        }
    }

    private fun showSkeletons() {
        adapter.showSkeletons()
    }

    private fun showResults() {
        _binding?.apply {
            llHistory.visibility = View.GONE
            llEmpty.visibility = View.GONE
            llNoResults.visibility = View.GONE
            swipeRefresh.visibility = View.VISIBLE
            llSortBar.visibility = View.VISIBLE
        }
    }

    private fun showNoResults(query: String) {
        _binding?.apply {
            llHistory.visibility = View.GONE
            llEmpty.visibility = View.GONE
            swipeRefresh.visibility = View.GONE
            llSortBar.visibility = View.GONE
            llNoResults.visibility = View.VISIBLE
            tvNoResults.text = "По запросу «$query» ничего не найдено"
        }
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(
            requireContext(),
            InputMethodManager::class.java
        )
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        searchDebounceJob?.cancel()
        _binding = null
    }
}
