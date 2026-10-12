package ru.asmr1.app.author

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.AuthorPageResponse
import ru.asmr1.app.databinding.ActivityAuthorPageBinding
import ru.asmr1.app.utils.formatViews
import ru.asmr1.app.utils.formatViewsShort

class AuthorPageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTHOR_ID = "AUTHOR_ID"
    }

    private lateinit var binding: ActivityAuthorPageBinding
    private var authorId: Int = -1
    private var authorData: AuthorPageResponse? = null

    // 0 = не подписан, 1 = подписан + уведомления, 2 = подписан без уведомлений
    private var subscriptionState: Int = 0
    private var subscribersCount: Int = 0
    private var subscribeInFlight: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthorPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authorId = intent.getIntExtra(EXTRA_AUTHOR_ID, -1)
        android.util.Log.d("AuthorPage", "onCreate: authorId = $authorId")
        if (authorId <= 0) {
            android.util.Log.e("AuthorPage", "authorId <= 0, finish()")
            finish()
            return
        }

        setupCloseButton()
        loadAuthor()
    }

    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadAuthor() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getUserPage(authorId)
                authorData = response
                bindAuthor(response)
                loadSubscriptionStatus()
            } catch (e: Exception) {
                android.util.Log.e("AuthorPage", "loadAuthor FAILED", e)
                Toast.makeText(
                    this@AuthorPageActivity,
                    "Ошибка загрузки: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun bindAuthor(data: AuthorPageResponse) {
        binding.tvNickname.text = data.nickname
        binding.tvUsername.text = "@" + data.nickname.lowercase().replace(" ", "_").replace("[^a-z0-9_]".toRegex(), "")
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
            binding.tvDescription.visibility = android.view.View.GONE
        }

        // Начальное состояние подписки из данных автора
        subscriptionState = if (data.followStatus) 1 else 0
        subscribersCount = data.subscribersCount
        updateSubscriptionUi()

        binding.btnSubscribe.setOnClickListener {
            if (subscriptionState == 0) performSubscribe("sub")
            else performSubscribe("unsub")
        }
        binding.btnBell.setOnClickListener { showSubscriptionMenu() }

        binding.btnMessage.setOnClickListener {
            android.widget.Toast.makeText(
                this@AuthorPageActivity,
                "Скоро",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        setupPager(data)
    }


    // ============================================================
    // ПОДПИСКА
    // ============================================================

    private fun loadSubscriptionStatus() {
        if (authorId <= 0) return
        lifecycleScope.launch {
            try {
                val token = ru.asmr1.app.auth.AuthManager(this@AuthorPageActivity).getToken()
                    ?: return@launch
                val response = ApiClient.createJwtService(token).getSubscriptionStatus(authorId)
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
        if (!ru.asmr1.app.auth.AuthManager(this).isLoggedIn()) {
            Toast.makeText(this, "Войдите чтобы подписаться", Toast.LENGTH_SHORT).show()
            return
        }
        if (authorId <= 0) return

        subscribeInFlight = true
        val oldState = subscriptionState
        val oldCount = subscribersCount

        // Оптимистичное обновление UI
        when (action) {
            "sub"    -> { subscriptionState = 1; subscribersCount = oldCount + 1 }
            "unsub"  -> { subscriptionState = 0; subscribersCount = (oldCount - 1).coerceAtLeast(0) }
            "notify" -> subscriptionState = 1
            "mute"   -> subscriptionState = 2
        }
        updateSubscriptionUi()

        lifecycleScope.launch {
            try {
                val token = ru.asmr1.app.auth.AuthManager(this@AuthorPageActivity).getToken()
                    ?: return@launch
                val response = ApiClient.createJwtService(token)
                    .subscribe(authorId, ru.asmr1.app.data.SubscribeRequest(action))
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
                    Toast.makeText(this@AuthorPageActivity, response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                subscriptionState = oldState
                subscribersCount = oldCount
                updateSubscriptionUi()
                Toast.makeText(this@AuthorPageActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
            } finally {
                subscribeInFlight = false
            }
        }
    }

    private fun updateSubscriptionUi() {
        when (subscriptionState) {
            0 -> {
                binding.btnSubscribe.visibility = android.view.View.VISIBLE
                binding.btnBell.visibility = android.view.View.GONE
                binding.btnSubscribe.text = "Подписаться"
            }
            else -> {
                binding.btnSubscribe.visibility = android.view.View.VISIBLE
                binding.btnSubscribe.text = "Отписаться"
                binding.btnBell.visibility = android.view.View.VISIBLE
                binding.bellIcon.setImageResource(
                    if (subscriptionState == 1) R.drawable.ic_nav_bell
                    else R.drawable.ic_bell_off
                )
            }
        }
    }

    private fun showSubscriptionMenu() {
        try {
            val sheetView = layoutInflater.inflate(R.layout.sheet_subscription_menu, null)
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            dialog.setContentView(sheetView)
            sheetView.findViewById<android.view.View>(R.id.subs_menu_notify)?.setOnClickListener {
                performSubscribe("notify"); dialog.dismiss()
            }
            sheetView.findViewById<android.view.View>(R.id.subs_menu_mute)?.setOnClickListener {
                performSubscribe("mute"); dialog.dismiss()
            }
            sheetView.findViewById<android.view.View>(R.id.subs_menu_unsubscribe)?.setOnClickListener {
                performSubscribe("unsub"); dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка меню", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPager(data: AuthorPageResponse) {
        val adapter = AuthorPagerAdapter(this, data)
        binding.viewPager.adapter = adapter

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
}
