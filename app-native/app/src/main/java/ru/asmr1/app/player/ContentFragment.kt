package ru.asmr1.app.player

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.SubscribeRequest
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentContentBinding
import ru.asmr1.app.download.DownloadStorage
import ru.asmr1.app.download.DownloadedFile
import ru.asmr1.app.download.Downloader
import ru.asmr1.app.utils.formatViews
import ru.asmr1.app.utils.timeAgo
import java.util.Locale

class ContentFragment : Fragment() {

    companion object {
        const val ARG_VIDEO = "video"
        fun newInstance(video: Video) = ContentFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_VIDEO, video) }
        }
    }

    private var _binding: FragmentContentBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private lateinit var downloadStorage: DownloadStorage
    private lateinit var downloader: Downloader

    private var video: Video? = null

    private var isLiked: Boolean = false
    private var likesCount: Int = 0
    private var likeInFlight: Boolean = false

    private var subscriptionState = 0
    private var subscribersCount: Int = 0
    private var subscribeInFlight: Boolean = false

    private var downloadJob: Job? = null
    private var isDownloading: Boolean = false

    private var isSavedToPlaylist: Boolean = false

    private lateinit var previewAdapter: CommentsPreviewAdapter

    private val sliderHandler = Handler(Looper.getMainLooper())
    private var sliderRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())
        downloadStorage = DownloadStorage(requireContext())
        downloader = Downloader(requireContext())

        video = requireArguments().getParcelable<Video>(ARG_VIDEO) ?: return
        val v = video!!

        binding.title.text = v.title
        binding.meta.text = "${formatViews(v.views)} • ${timeAgo(v.published)}"

        binding.authorName.text = v.author.name
        binding.authorSubs.text = "0 подписчиков"
        if (v.author.avatar.isNotEmpty()) {
            binding.authorAvatar.load(v.author.avatar) {
                crossfade(true)
                placeholder(R.color.card_bg)
            }

            // Клик по аватору автора → страница автора
            val goToAuthor = {
                val act = requireActivity() as? ru.asmr1.app.MainActivity
                if (act != null) {
                    act.collapseToMini()
                    act.openAuthorPage(v.author.id)
                }
            }
            binding.authorAvatar.setOnClickListener { goToAuthor() }
            binding.authorName.setOnClickListener { goToAuthor() }
        }

        likesCount = v.likes
        updateLikeUi()
        updateSubscriptionUi()
        updateDownloadUi()
        setWatchLaterState(false)

        loadLikeStatus(v.id)
        loadSubscriptionStatus(v.author.id)
        loadPlaylistState(v.id)

        binding.btnSubscribe.setOnClickListener { performSubscribe("sub") }
        binding.btnBell.setOnClickListener { showSubscriptionMenu() }
        binding.titleBlock.setOnClickListener {
            (requireActivity() as MainActivity).openDescription(v)
        }
        binding.btnLike.setOnClickListener { performToggleLike() }

        binding.btnDownload.setOnClickListener {
            if (isDownloading) {
                Toast.makeText(requireContext(), "Идёт загрузка...", Toast.LENGTH_SHORT).show()
            } else {
                showDownloadMenu()
            }
        }

        binding.btnWatchLater.setOnClickListener {
            val sheet = AddToPlaylistSheet.newInstance(v.id)
            sheet.onAdded = { _ -> setWatchLaterState(true) }
            sheet.show(childFragmentManager, "add_to_playlist")
        }

        binding.btnShare.setOnClickListener {
            Toast.makeText(requireContext(), "Поделиться — скоро", Toast.LENGTH_SHORT).show()
        }
        binding.btnReport.setOnClickListener {
            Toast.makeText(requireContext(), "Репорт — скоро", Toast.LENGTH_SHORT).show()
        }

        setupCommentsPreview(v)
    }

    // ============================================================
    // СОСТОЯНИЕ КНОПКИ «СОХРАНИТЬ»
    // ============================================================
    private fun setWatchLaterState(saved: Boolean) {
        isSavedToPlaylist = saved
        _binding?.apply {
            btnWatchLaterIcon.setImageResource(
                if (saved) R.drawable.ic_player_bookmark_filled
                else R.drawable.ic_player_bookmark
            )
            btnWatchLaterText.text = if (saved) "Сохранено" else "Позже"
            btnWatchLaterText.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (saved) R.color.text_primary else R.color.text_secondary
                )
            )
        }
    }

    private fun loadPlaylistState(videoId: Int) {
        if (!authManager.isLoggedIn()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).getVideoPlaylists(videoId)
                if (_binding != null && response.success && response.playlistIds.isNotEmpty()) {
                    setWatchLaterState(true)
                }
            } catch (_: Exception) {}
        }
    }

    // ============================================================
    // ПРЕВЬЮ КОММЕНТАРИЕВ
    // ============================================================
    private fun setupCommentsPreview(v: Video) {
        previewAdapter = CommentsPreviewAdapter { comment ->
            (requireActivity() as MainActivity).openCommentsWithFocus(v, comment)
        }
        binding.commentsPreviewPager.adapter = previewAdapter
        binding.commentsPreviewPager.offscreenPageLimit = 2
        binding.commentsPreviewPager.setPageTransformer { page, position ->
            val scale = 0.9f + (1 - kotlin.math.abs(position)) * 0.1f
            page.scaleY = scale
            page.scaleX = scale
            page.alpha = 0.7f + (1 - kotlin.math.abs(position)) * 0.3f
        }
        binding.commentsHeaderWrap.setOnClickListener {
            (requireActivity() as MainActivity).openComments(v)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken()
                val api = if (token != null) {
                    ApiClient.createJwtService(token)
                } else {
                    ApiClient.service
                }
                val response = api.getComments(
                    videoId = v.id,
                    page = 1,
                    perPage = 10,
                    order = "new"
                )
                if (_binding == null) return@launch

                if (response.success && response.comments.isNotEmpty()) {
                    binding.commentsSliderWrap.visibility = View.VISIBLE
                    binding.commentsCountPreview.text = response.total.toString()
                    previewAdapter.setItems(response.comments)
                    startSliderAutoScroll()
                } else {
                    binding.commentsSliderWrap.visibility = View.GONE
                }
            } catch (_: Exception) {
                if (_binding != null) {
                    binding.commentsSliderWrap.visibility = View.GONE
                }
            }
        }
    }

    private fun startSliderAutoScroll() {
        stopSliderAutoScroll()
        if (previewAdapter.itemCount <= 1) return

        val r = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val pager = binding.commentsPreviewPager
                val count = pager.adapter?.itemCount ?: 0
                if (count <= 1) return
                val next = (pager.currentItem + 1) % count
                pager.setCurrentItem(next, true)
                sliderHandler.postDelayed(this, 7000L)
            }
        }
        sliderRunnable = r
        sliderHandler.postDelayed(r, 7000L)
    }

    private fun stopSliderAutoScroll() {
        sliderRunnable?.let { sliderHandler.removeCallbacks(it) }
        sliderRunnable = null
    }

    // ============================================================
    // DOWNLOAD
    // ============================================================
    private fun updateDownloadUi() {
        val v = video ?: return
        _binding?.apply {
            val hasVideo = downloadStorage.hasVideo(v.id)
            val hasAudio = downloadStorage.hasAudio(v.id)

            val primaryTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.text_primary)
            )
            val secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            val primaryColor = ContextCompat.getColor(requireContext(), R.color.text_primary)

            when {
                hasVideo -> {
                    btnDownloadIcon.setImageResource(R.drawable.ic_download_video)
                    ImageViewCompat.setImageTintList(btnDownloadIcon, primaryTint)
                    btnDownloadText.text = "Скачано"
                    btnDownloadText.setTextColor(primaryColor)
                }
                hasAudio -> {
                    btnDownloadIcon.setImageResource(R.drawable.ic_download_audio)
                    ImageViewCompat.setImageTintList(btnDownloadIcon, primaryTint)
                    btnDownloadText.text = "Скачано"
                    btnDownloadText.setTextColor(primaryColor)
                }
                else -> {
                    btnDownloadIcon.setImageResource(R.drawable.ic_player_download)
                    ImageViewCompat.setImageTintList(btnDownloadIcon, primaryTint)
                    btnDownloadText.text = "Скачать"
                    btnDownloadText.setTextColor(secondaryColor)
                }
            }

            if (!isDownloading) {
                downloadProgressOverlay.hide()
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f ГБ", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.0f МБ", mb)
            else -> String.format(Locale.US, "%.0f КБ", kb)
        }
    }

    private fun showDownloadMenu() {
        val v = video ?: return

        val sheetView = layoutInflater.inflate(R.layout.sheet_download_menu, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetView)

        val menuVideo = sheetView.findViewById<View>(R.id.menu_download_video)
        val menuAudio = sheetView.findViewById<View>(R.id.menu_download_audio)
        val menuVideoText = sheetView.findViewById<TextView>(R.id.menu_download_video_text)
        val menuAudioText = sheetView.findViewById<TextView>(R.id.menu_download_audio_text)
        val deleteVideo = sheetView.findViewById<ImageButton>(R.id.menu_delete_video)
        val deleteAudio = sheetView.findViewById<ImageButton>(R.id.menu_delete_audio)
        val cancel = sheetView.findViewById<View>(R.id.menu_download_cancel)

        val hasVideo = downloadStorage.hasVideo(v.id)
        val hasAudio = downloadStorage.hasAudio(v.id)

        if (hasVideo) {
            menuVideoText.text = "Скачать видео"
            menuVideoText.alpha = 0.4f
            menuVideo.alpha = 0.4f
            menuVideo.isEnabled = false
            menuVideo.isClickable = false
            deleteVideo.visibility = View.VISIBLE
        } else {
            menuVideoText.text = "Скачать видео"
        }

        if (hasAudio) {
            menuAudioText.text = "Скачать только аудио"
            menuAudioText.alpha = 0.4f
            menuAudio.alpha = 0.4f
            menuAudio.isEnabled = false
            menuAudio.isClickable = false
            deleteAudio.visibility = View.VISIBLE
        } else {
            menuAudioText.text = "Скачать только аудио"
        }

        if (v.audioUrl.isNullOrEmpty()) {
            menuAudioText.text = "Скачать только аудио (нет)"
            menuAudioText.alpha = 0.4f
            menuAudio.alpha = 0.4f
            menuAudio.isEnabled = false
            menuAudio.isClickable = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            if (!hasVideo && !v.videoUrl.isNullOrEmpty()) {
                val size = downloader.getRemoteFileSize(v.videoUrl)
                if (_binding != null && dialog.isShowing) {
                    menuVideoText.text = "Скачать видео" + if (size > 0) "  ${formatSize(size)}" else ""
                }
            }
            if (!hasAudio && !v.audioUrl.isNullOrEmpty()) {
                val size = downloader.getRemoteFileSize(v.audioUrl)
                if (_binding != null && dialog.isShowing) {
                    menuAudioText.text = "Скачать только аудио" + if (size > 0) "  ${formatSize(size)}" else ""
                }
            }
        }

        menuVideo.setOnClickListener {
            if (hasVideo || v.videoUrl.isNullOrEmpty()) return@setOnClickListener
            dialog.dismiss()
            startDownload(DownloadedFile.Type.VIDEO, v.videoUrl, "mp4")
        }

        menuAudio.setOnClickListener {
            if (hasAudio || v.audioUrl.isNullOrEmpty()) return@setOnClickListener
            dialog.dismiss()
            startDownload(DownloadedFile.Type.AUDIO, v.audioUrl, "mp3")
        }

        deleteVideo.setOnClickListener {
            downloader.delete(v.id, DownloadedFile.Type.VIDEO)
            Toast.makeText(requireContext(), "Видео удалено", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            updateDownloadUi()
        }

        deleteAudio.setOnClickListener {
            downloader.delete(v.id, DownloadedFile.Type.AUDIO)
            Toast.makeText(requireContext(), "Аудио удалено", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            updateDownloadUi()
        }

        cancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun startDownload(type: DownloadedFile.Type, url: String, extension: String) {
        val v = video ?: return
        if (isDownloading) return

        isDownloading = true
        _binding?.apply {
            btnDownloadText.text = "0%"
            btnDownloadText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            downloadProgressOverlay.setProgressPercent(0)
        }

        downloadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                downloader.download(v, type, url, extension).collect { progress ->
                    if (_binding == null) return@collect

                    if (progress.isDone) {
                        isDownloading = false
                        updateDownloadUi()
                        Toast.makeText(
                            requireContext(),
                            if (type == DownloadedFile.Type.VIDEO) "Видео скачано" else "Аудио скачано",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        _binding?.apply {
                            downloadProgressOverlay.setProgressPercent(progress.progressPercent)
                            btnDownloadText.text = "${progress.progressPercent}%"
                        }
                    }
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                isDownloading = false
                updateDownloadUi()
                Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================================
    // ЛАЙК
    // ============================================================
    private fun loadLikeStatus(videoId: Int) {
        if (!authManager.isLoggedIn()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).getLikeStatus(videoId)
                if (_binding == null) return@launch
                if (response.success) {
                    likesCount = response.likes
                    isLiked = response.currentVote == "like"
                    updateLikeUi()
                }
            } catch (_: Exception) { }
        }
    }

    private fun performToggleLike() {
        if (likeInFlight) return
        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите чтобы поставить лайк", Toast.LENGTH_SHORT).show()
            return
        }
        val videoId = video?.id ?: return
        likeInFlight = true
        val wasLiked = isLiked
        isLiked = !wasLiked
        likesCount = if (isLiked) likesCount + 1 else (likesCount - 1).coerceAtLeast(0)
        updateLikeUi()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).toggleLike(videoId)
                if (_binding == null) return@launch
                if (response.success) {
                    likesCount = response.likes
                    isLiked = response.currentVote == "like"
                    updateLikeUi()
                } else {
                    isLiked = wasLiked
                    updateLikeUi()
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                isLiked = wasLiked
                updateLikeUi()
                Toast.makeText(requireContext(), "Ошибка сети", Toast.LENGTH_SHORT).show()
            } finally {
                likeInFlight = false
            }
        }
    }

    private fun updateLikeUi() {
        _binding?.apply {
            btnLikeIcon.setImageResource(
                if (isLiked) R.drawable.ic_player_like_filled else R.drawable.ic_player_like
            )
            btnLikeText.text = if (likesCount > 0) likesCount.toString() else "Нравится"
            if (isLiked) {
                ImageViewCompat.setImageTintList(btnLikeIcon, null)
            } else {
                ImageViewCompat.setImageTintList(
                    btnLikeIcon,
                    ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.text_primary)
                    )
                )
            }
            btnLikeText.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isLiked) R.color.accent_red else R.color.text_secondary
                )
            )
        }
    }

    // ============================================================
    // ПОДПИСКА
    // ============================================================
    private fun loadSubscriptionStatus(authorId: Int) {
        if (!authManager.isLoggedIn()) return
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
        val authorId = video?.author?.id ?: return
        subscribeInFlight = true
        val oldState = subscriptionState
        val oldCount = subscribersCount
        when (action) {
            "sub" -> { subscriptionState = 1; subscribersCount = oldCount + 1 }
            "unsub" -> { subscriptionState = 0; subscribersCount = (oldCount - 1).coerceAtLeast(0) }
            "notify" -> subscriptionState = 1
            "mute" -> subscriptionState = 2
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
        _binding?.apply {
            when (subscriptionState) {
                0 -> {
                    btnSubscribe.visibility = View.VISIBLE
                    btnBell.visibility = View.GONE
                }
                else -> {
                    btnSubscribe.visibility = View.GONE
                    btnBell.visibility = View.VISIBLE
                    bellIcon.setImageResource(
                        if (subscriptionState == 1) R.drawable.ic_nav_bell
                        else R.drawable.ic_bell_off
                    )
                }
            }
            authorSubs.text = "$subscribersCount подписчиков"
        }
    }

    private fun showSubscriptionMenu() {
        try {
            val sheetView = layoutInflater.inflate(R.layout.sheet_subscription_menu, null)
            val dialog = BottomSheetDialog(requireContext())
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

    override fun onDestroyView() {
        super.onDestroyView()
        downloadJob?.cancel()
        stopSliderAutoScroll()
        _binding = null
    }
}
