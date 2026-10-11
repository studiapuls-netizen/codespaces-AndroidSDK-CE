package ru.asmr1.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private val tabTitles = arrayOf("Все", "Видео", "Комментарии")
    private val tabFilters = arrayOf("all", "video", "reply")

    private var pagerAdapter: FragmentStateAdapter? = null
    private var unreadOnly: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        if (!authManager.isLoggedIn()) {
            binding.notifEmpty.visibility = View.VISIBLE
            binding.notifPager.visibility = View.GONE
            binding.notifTabs.visibility = View.GONE
            binding.markAllRead.visibility = View.GONE
            binding.llFilterBar.visibility = View.GONE
            return
        }

        setupPager()
        setupMarkAllRead()
        setupChips()
    }

    private fun setupPager() {
        pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabFilters.size
            override fun createFragment(position: Int): Fragment {
                return NotificationsTabFragment.newInstance(tabFilters[position], unreadOnly)
            }
        }
        binding.notifPager.adapter = pagerAdapter
        binding.notifPager.offscreenPageLimit = 1

        TabLayoutMediator(binding.notifTabs, binding.notifPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun setupMarkAllRead() {
        binding.markAllRead.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val token = authManager.getToken() ?: return@launch
                    val response = ApiClient.createJwtService(token).markAllNotificationsRead()
                    if (_binding == null) return@launch
                    if (response.success) {
                        Toast.makeText(requireContext(), "Все прочитано", Toast.LENGTH_SHORT).show()
                        reloadAllTabs()
                    }
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Ошибка сети", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupChips() {
        updateChipsUI()
        binding.chipAll.setOnClickListener {
            if (!unreadOnly) return@setOnClickListener
            unreadOnly = false
            updateChipsUI()
            reloadAllTabs()
        }
        binding.chipUnread.setOnClickListener {
            if (unreadOnly) return@setOnClickListener
            unreadOnly = true
            updateChipsUI()
            reloadAllTabs()
        }
    }

    private fun updateChipsUI() {
        setChipActive(binding.chipAll, !unreadOnly)
        setChipActive(binding.chipUnread, unreadOnly)
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

    private fun reloadAllTabs() {
        // Пересоздаём адаптер (у NotificationsTabFragment параметр unreadOnly меняется)
        setupPager()
        binding.notifPager.setCurrentItem(0, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
