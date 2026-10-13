package ru.asmr1.app.author

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.AuthorPageResponse
import ru.asmr1.app.data.SubscribeRequest
import ru.asmr1.app.databinding.FragmentAuthorPageBinding
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.utils.formatViewsShort

class AuthorPageFragment : Fragment() {

    companion object {
        const val ARG_AUTHOR_ID = "AUTHOR_ID"
        fun newInstance(authorId: Int) = AuthorPageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_AUTHOR_ID, authorId) }
        }
    }

    private var _binding: FragmentAuthorPageBinding? = null
    private val binding get() = _binding!!

    private var authorId: Int = -1
    private var authorData: AuthorPageResponse? = null

    private var subscriptionState: Int = 0
    private var subscribersCount: Int = 0
    private var subscribeInFlight: Boolean = false

    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthorPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        // Жёстко скрываем чипы при старте — они появятся только на вкладке "Видео"

        authorId = arguments?.getInt(ARG_AUTHOR_ID, -1) ?: -1
        if (authorId <= 0) {
            (activity as? ru.asmr1.app.MainActivity)?.closeAuthorPage()
            return
        }

        binding.btnClose.setOnClickListener {
            (activity as? ru.asmr1.app.MainActivity)?.closeAuthorPage()
        }

        // Клик по описанию → вкладка "Об авторе"
        binding.tvDescription.setOnClickListener {
            binding.viewPager.setCurrentItem(3, true)
        }

        setupSwipeToClose()

        binding.btnSubscribe.setOnClickListener {
            if (subscriptionState == 0) performSubscribe("sub")
            else performSubscribe("unsub")
        }
        binding.btnBell.setOnClickListener { showSubscriptionMenu() }

        // Клик по аватару → модалка с увеличенным аватаром
        binding.ivAvatar.setOnClickListener {
            val url = authorData?.avatar ?: return@setOnClickListener
            showAvatarPreview(url)
        }

        loadAuthor()
    }

    private fun loadAuthor() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.service.getUserPage(authorId)
                if (_binding == null) return@launch
                authorData = response
                bindAuthor(response)
                loadSubscriptionStatus()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                (activity as? ru.asmr1.app.MainActivity)?.closeAuthorPage()
            }
        }
    }

    private fun bindAuthor(data: AuthorPageResponse) {
        val uname = "@" + data.nickname.lowercase().replace(" ", "_").replace("[^a-z0-9_]".toRegex(), "")

        binding.tvNickname.text = data.nickname
        binding.tvUsername.text = uname
        binding.tvSubscribers.text = formatViewsShort(data.subscribersCount)
        binding.tvVideos.text = data.videoCount.toString()
        binding.tvViews.text = formatViewsShort(data.totalViews)

        if (data.avatar.isNotEmpty()) {
            binding.ivAvatar.load(data.avatar) {
                crossfade(true)
                placeholder(R.color.card_bg)
                error(R.color.card_bg)
            }
        }

        if (!data.description.isNullOrEmpty()) {
            binding.tvDescription.text = data.description
        } else {
            binding.tvDescription.visibility = View.GONE
        }

        subscriptionState = if (data.followStatus) 1 else 0
        subscribersCount = data.subscribersCount
        updateSubscriptionUi()

        setupPager(data)
        setupAuthorVideoChips()
    }


    // ============================================================
    // ЧИПЫ СОРТИРОВКИ ВИДЕО (в activity_main.xml, внизу над меню)
    // ============================================================

    private fun setupAuthorVideoChips() {
        val chips = activity?.findViewById<android.view.View>(R.id.author_video_chips) ?: return
        val chipNew = activity?.findViewById<android.widget.TextView>(R.id.author_chip_new)
        val chipOld = activity?.findViewById<android.widget.TextView>(R.id.author_chip_old)
        val chipViews = activity?.findViewById<android.widget.TextView>(R.id.author_chip_views)

        fun refresh(mode: String) {
            fun setChip(chip: android.widget.TextView?, active: Boolean) {
                chip?.isSelected = active
                chip?.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        requireContext(),
                        if (active) android.R.color.white else R.color.text_secondary
                    )
                )
            }
            setChip(chipNew, mode == "new")
            setChip(chipOld, mode == "old")
            setChip(chipViews, mode == "views")
        }

        fun getVideosFragment(): AuthorVideosFragment? {
            // ViewPager2 не даёт теги f0/f1 — ищем по типу рекурсивно
            fun searchRecursive(fm: androidx.fragment.app.FragmentManager): AuthorVideosFragment? {
                for (f in fm.fragments) {
                    if (f is AuthorVideosFragment) return f
                    val found = searchRecursive(f.childFragmentManager)
                    if (found != null) return found
                }
                return null
            }
            return searchRecursive(childFragmentManager)
        }

        fun setChipsVisible(visible: Boolean) {
            chips.animate().cancel()
            chips.animate().alpha(if (visible) 1f else 0f).setDuration(150).start()
        }

        chipNew?.setOnClickListener { getVideosFragment()?.setSort("new"); refresh("new") }
        chipOld?.setOnClickListener { getVideosFragment()?.setSort("old"); refresh("old") }
        chipViews?.setOnClickListener { getVideosFragment()?.setSort("views"); refresh("views") }

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setChipsVisible(position == 1)
                if (position == 1) refresh(getVideosFragment()?.getSortMode() ?: "new")
            }
        })

        setChipsVisible(binding.viewPager.currentItem == 1)
        refresh("new")
    }

    private fun setupPager(data: AuthorPageResponse) {
        val adapter = AuthorPagerAdapter(requireActivity(), data)
        binding.viewPager.adapter = adapter
        // Предзагрузка всех 4 вкладок — убирает лаг при первом переключении
        binding.viewPager.offscreenPageLimit = 4

        com.google.android.material.tabs.TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager
        ) { tab, position ->
            tab.text = when (position) {
                0 -> "Главная"
                1 -> "Видео"
                2 -> "Плейлисты"
                3 -> "Об авторе"
                else -> ""
            }
        }.attach()
    }

    // ============================================================
    // ПОДПИСКА
    // ============================================================

    private fun loadSubscriptionStatus() {
        if (authorId <= 0) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).getSubscriptionStatus(authorId)
                if (_binding == null) return@launch
                if (response.success) {
                    subscribersCount = response.subscribersCount
                    subscriptionState = when {
                        !response.subscribed -> 0
                        response.notificationsEnabled -> 1
                        else -> 2
                    }
                    updateSubscriptionUi()
                }
            } catch (_: Exception) { }
        }
    }

    private fun performSubscribe(action: String) {
        if (subscribeInFlight) return
        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите чтобы подписаться", Toast.LENGTH_SHORT).show()
            return
        }
        if (authorId <= 0) return

        subscribeInFlight = true
        val oldState = subscriptionState
        val oldCount = subscribersCount

        when (action) {
            "sub"    -> { subscriptionState = 1; subscribersCount = oldCount + 1 }
            "unsub"  -> { subscriptionState = 0; subscribersCount = (oldCount - 1).coerceAtLeast(0) }
            "notify" -> subscriptionState = 1
            "mute"   -> subscriptionState = 2
        }
        updateSubscriptionUi()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token)
                    .subscribe(authorId, SubscribeRequest(action))
                if (_binding == null) return@launch
                if (response.success) {
                    subscribersCount = response.subscribersCount
                    subscriptionState = when {
                        !response.subscribed -> 0
                        response.notificationsEnabled -> 1
                        else -> 2
                    }
                    updateSubscriptionUi()
                } else {
                    subscriptionState = oldState
                    subscribersCount = oldCount
                    updateSubscriptionUi()
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                subscriptionState = oldState
                subscribersCount = oldCount
                updateSubscriptionUi()
                Toast.makeText(requireContext(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            } finally {
                subscribeInFlight = false
            }
        }
    }

    private fun updateSubscriptionUi() {
        if (_binding == null) return
        val subscribed = subscriptionState != 0
        val iconRes = if (subscriptionState == 1) R.drawable.ic_nav_bell else R.drawable.ic_bell_off

        if (!subscribed) {
            binding.btnSubscribe.visibility = android.view.View.VISIBLE
            binding.btnSubscribe.text = "Подписаться"
            binding.btnBell.visibility = android.view.View.GONE
        } else {
            binding.btnSubscribe.visibility = android.view.View.GONE
            binding.btnBell.visibility = android.view.View.VISIBLE
            binding.bellIcon.setImageResource(iconRes)
        }
    }

    private fun showSubscriptionMenu() {
        try {
            val sheetView = layoutInflater.inflate(R.layout.sheet_subscription_menu, null)
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
            dialog.setContentView(sheetView)
            sheetView.findViewById<View>(R.id.subs_menu_notify)?.setOnClickListener {
                performSubscribe("notify"); dialog.dismiss()
            }
            sheetView.findViewById<View>(R.id.subs_menu_mute)?.setOnClickListener {
                performSubscribe("mute"); dialog.dismiss()
            }
            sheetView.findViewById<View>(R.id.subs_menu_unsubscribe)?.setOnClickListener {
                performSubscribe("unsub"); dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка меню", Toast.LENGTH_SHORT).show()
        }
    }


    // ============================================================
    // СВАЙП ВНИЗ ПО ШАПКЕ = ЗАКРЫТИЕ СТРАНИЦЫ АВТОРА
    // ============================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToClose() {
        val activity = activity as? ru.asmr1.app.MainActivity ?: return
        val container = activity.findViewById<android.view.View>(R.id.author_container) ?: return
        val appBar = binding.headerBlock

        var startY = 0f
        var isDragging = false
        val thresholdPx = 150 * resources.displayMetrics.density

        appBar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (dy > 20f) {
                        isDragging = true
                        // Только вниз, не вверх
                        container.translationY = dy.coerceAtLeast(0f)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = event.rawY - startY
                    if (isDragging && dy > thresholdPx) {
                        // Закрываем
                        container.translationY = 0f
                        activity.closeAuthorPage()
                    } else {
                        // Возвращаем
                        container.animate()
                            .translationY(0f)
                            .setDuration(180)
                            .start()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }





    // ============================================================
    // ПРОКИДКА СКРОЛЛА В AppBarLayout
    // ============================================================

    fun scrollAppBar(dy: Int) {
        if (!isAdded) return
        if (_binding == null) return
        binding.appBar.setExpanded(dy <= 0, true)
    }


    // ============================================================
    // МОДАЛКА С УВЕЛИЧЕННЫМ АВАТАРОМ
    // ============================================================

    private fun showAvatarPreview(url: String) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val view = layoutInflater.inflate(R.layout.dialog_avatar_preview, null)
        dialog.setContentView(view)

        val img = view.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.avatar_preview_image)
        val closeBtn = view.findViewById<android.widget.ImageButton>(R.id.avatar_preview_close)
        val root = view.findViewById<android.view.View>(R.id.avatar_preview_root)

        img.load(url) {
            crossfade(true)
            placeholder(R.color.card_bg)
            error(R.color.card_bg)
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        root.setOnClickListener { dialog.dismiss() }
        img.setOnClickListener { /* не закрываем при клике на аватар */ }

        dialog.setOnShowListener {
            val window = dialog.window
            if (window != null) {
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.setDimAmount(0f)
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.findViewById<android.view.View>(R.id.author_video_chips)
            ?.animate()?.alpha(0f)?.setDuration(120)?.start()
        _binding = null
    }
}
