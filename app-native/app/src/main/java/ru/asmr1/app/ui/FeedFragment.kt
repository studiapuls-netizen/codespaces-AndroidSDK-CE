package ru.asmr1.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import ru.asmr1.app.databinding.FragmentFeedBinding
import ru.asmr1.app.download.DownloadStorage
import ru.asmr1.app.utils.NetworkUtils

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    // Динамический список табов: "recommended" | "latest" | "downloads"
    private val tabs = mutableListOf<String>()

    // Индекс таба «Скачано» (-1 если его нет)
    private var downloadsTabIndex = -1

    // ===== Слушатель изменений в SharedPreferences («asmr1_downloads») =====
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

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

        // ===== СОБИРАЕМ СПИСОК ТАБОВ =====
        buildTabsList()

        // ===== НАСТРАИВАЕМ ViewPager2 =====
        setupViewPager()

        // ===== КЛИКИ ПО ТАБАМ =====
        binding.tabRecommended.setOnClickListener { scrollToTab("recommended") }
        binding.tabLatest.setOnClickListener { scrollToTab("latest") }
        binding.tabDownloads.setOnClickListener { scrollToTab("downloads") }

        // ===== УПРАВЛЯЕМ ВИДИМОСТЬЮ ТАБА «СКАЧАНО» =====
        updateDownloadsTabVisibility()

        // ===== СТАРТОВАЯ СТРАНИЦА =====
        val initialIndex = if (downloadsTabIndex >= 0 &&
            !NetworkUtils.isOnline(requireContext())
        ) {
            // Офлайн → сразу на «Скачано»
            downloadsTabIndex
        } else {
            // Онлайн → на первый таб (В тренде)
            0
        }

        binding.viewPager.setCurrentItem(initialIndex, false)
        applyTabStates(initialIndex.toFloat())

        // ===== СЛУШАЕМ ИЗМЕНЕНИЯ =====
        subscribeToDownloadChanges()
    }

    /**
     * Настраивает ViewPager2 с адаптером на основе текущего списка tabs.
     * Вызывается и при первом старте, и при пересборке адаптера.
     */
    private fun setupViewPager() {
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = tabs.size
            override fun createFragment(position: Int): Fragment {
                return when (tabs.getOrNull(position)) {
                    "downloads" -> DownloadsFragment()
                    "latest" -> VideoListFragment.newInstance("latest")
                    else -> VideoListFragment.newInstance("recommended")
                }
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                applyTabStates(position + positionOffset)
            }

            override fun onPageSelected(position: Int) {
                for (i in tabs.indices) {
                    if (i != position) {
                        scrollToTopAt(i)
                    }
                }
            }
        })
    }

    /**
     * Подписка на изменения в prefs "asmr1_downloads".
     * Когда что-то скачано/удалено — пересобираем табы.
     */
    private fun subscribeToDownloadChanges() {
        val prefs = requireContext()
            .getSharedPreferences("asmr1_downloads", Context.MODE_PRIVATE)

        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "downloads_index") {
                // Что-то изменилось в списке загрузок
                refreshTabsIfNeeded()
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /**
     * Пересобирает список табов. Если состав изменился —
     * пересоздаёт адаптер, чтобы ViewPager2 подхватил новый таб.
     */
    private fun refreshTabsIfNeeded() {
        if (_binding == null) return

        val oldIndex = downloadsTabIndex
        val oldCount = tabs.size

        buildTabsList()
        updateDownloadsTabVisibility()

        if (tabs.size != oldCount || downloadsTabIndex != oldIndex) {
            // Количество табов изменилось — пересоздаём адаптер
            setupViewPager()
            // Возвращаемся на первый таб (или сохраняем текущую позицию если возможно)
            val newIndex = if (downloadsTabIndex >= 0 &&
                !NetworkUtils.isOnline(requireContext())
            ) {
                downloadsTabIndex
            } else {
                0
            }
            binding.viewPager.setCurrentItem(newIndex, false)
            applyTabStates(newIndex.toFloat())
        }
    }

    /**
     * Собирает список табов на основе состояния.
     */
    private fun buildTabsList() {
        tabs.clear()
        downloadsTabIndex = -1

        val storage = DownloadStorage(requireContext())
        val hasDownloads = storage.hasAnyDownloads()
        val isOnline = NetworkUtils.isOnline(requireContext())

        tabs.add("recommended")
        tabs.add("latest")

        if (hasDownloads || !isOnline) {
            tabs.add("downloads")
            downloadsTabIndex = 2
        }
    }

    private fun updateDownloadsTabVisibility() {
        binding.tabDownloads.visibility = if (downloadsTabIndex >= 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun scrollToTab(name: String) {
        val index = tabs.indexOf(name)
        if (index < 0) return
        if (binding.viewPager.currentItem != index) {
            binding.viewPager.setCurrentItem(index, true)
        }
    }

    private fun scrollToTopAt(index: Int) {
        val tag = "f$index"
        val frag = childFragmentManager.findFragmentByTag(tag)
        if (frag is VideoListFragment) {
            frag.scrollToTop()
        }
    }

    private fun applyTabStates(progress: Float) {
        for (i in tabs.indices) {
            val dist = kotlin.math.abs(progress - i)
            val weight = (1f - dist).coerceIn(0f, 1f)

            val tabView = when (tabs[i]) {
                "recommended" -> binding.tabRecommended
                "latest" -> binding.tabLatest
                "downloads" -> binding.tabDownloads
                else -> null
            }
            tabView?.let { applyTabStateToView(it, weight) }
        }
    }

    private fun applyTabStateToView(tab: View, weight: Float) {
        val scale = 0.94f + 0.06f * weight
        tab.scaleX = scale
        tab.scaleY = scale
        tab.alpha = 0.6f + 0.4f * weight
        tab.background?.mutate()?.alpha = (weight * 255).toInt().coerceIn(0, 255)
    }

    override fun onResume() {
        super.onResume()
        // На всякий случай — проверяем и здесь (если что-то поменялось через другие пути)
        refreshTabsIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Отписываемся от prefs
        if (prefsListener != null) {
            val prefs = requireContext()
                .getSharedPreferences("asmr1_downloads", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            prefsListener = null
        }

        _binding = null
    }
}
