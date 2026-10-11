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

class AuthorPageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTHOR_ID = "AUTHOR_ID"
    }

    private lateinit var binding: ActivityAuthorPageBinding
    private var authorId: Int = -1
    private var authorData: AuthorPageResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthorPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authorId = intent.getIntExtra(EXTRA_AUTHOR_ID, -1)
        if (authorId <= 0) {
            finish()
            return
        }

        setupToolbar()
        loadAuthor()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadAuthor() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getUserPage(authorId)
                authorData = response
                bindAuthor(response)
            } catch (e: Exception) {
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
        binding.tvSubscribers.text = formatViews(data.subscribersCount)
        binding.tvVideos.text = data.videoCount.toString()

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

        binding.btnSubscribe.text = if (data.followStatus) "Отписаться" else "Подписаться"

        setupPager(data)
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
