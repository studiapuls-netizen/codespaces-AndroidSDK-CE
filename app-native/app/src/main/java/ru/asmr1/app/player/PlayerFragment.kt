package ru.asmr1.app.player

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentPlayerBinding
import ru.asmr1.app.download.PlaybackModeResolver
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerFragment : Fragment() {

    companion object {
        const val ARG_VIDEO = "video"
        fun newInstance(video: Video) = PlayerFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_VIDEO, video) }
        }
        private const val FS_SIDE_DP = 48f
        private const val FS_BOTTOM_DP = 45f
        private const val NORMAL_SIDE_DP = 12f
        private const val NORMAL_BOTTOM_TIME_DP = 12f
        private const val NORMAL_END_FS_DP = 8f

        private const val THUMB_SIZE_DP = 10f
        private const val PALETTE_BRIGHTNESS = 0.45f

        private const val RAPID_SEEK_WINDOW_MS = 1500L
    }

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var controlsVisible = false
    private val handler = Handler(Looper.getMainLooper())

    private var playerListener: Player.Listener? = null
    private var userSeeking = false

    private var isFullscreen = false
    private var gestureDetector: GestureDetector? = null
    private var isLongPressing = false

    private var isAudioMode = false

    private var accumulatedSeekMs = 0L
    private var lastSeekTapTime = 0L
    private val seekResetRunnable = Runnable {
        accumulatedSeekMs = 0L
    }

    private var rapidSeekActive = false
    private val rapidSeekResetRunnable = Runnable {
        rapidSeekActive = false
        accumulatedSeekMs = 0L
    }

    private val longPressRunnable = Runnable {
        isLongPressing = true
        MainActivity.globalPlayer?.setPlaybackSpeed(2.0f)
        showSpeedIndicator()
    }

    fun isFullscreenMode(): Boolean = isFullscreen

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun isPointInsidePlayerView(e: MotionEvent): Boolean {
        val view = binding.playerView
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = e.rawX
        val y = e.rawY
        return x >= loc[0] && x <= loc[0] + view.width &&
                y >= loc[1] && y <= loc[1] + view.height
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val screenWidth = resources.displayMetrics.widthPixels
        val targetHeight = (screenWidth * 9f / 16f).toInt()

        binding.playerFrame.layoutParams = binding.playerFrame.layoutParams.apply {
            height = targetHeight
        }
        binding.playerView.layoutParams = binding.playerView.layoutParams.apply {
            height = targetHeight
        }
        binding.audioPreview.layoutParams = binding.audioPreview.layoutParams.apply {
            height = targetHeight
        }

        binding.playerView.player = MainActivity.globalPlayer

        // ===== ОПРЕДЕЛЕНИЕ РЕЖИМА ВОСПРОИЗВЕДЕНИЯ =====
        val video = requireArguments().getParcelable<Video>(ARG_VIDEO)
        if (video != null) {
            val mode = PlaybackModeResolver.resolve(requireContext(), video)
            when (mode) {
                is PlaybackModeResolver.Mode.AudioMode -> {
                    isAudioMode = true
                    binding.playerView.visibility = View.GONE
                    binding.audioPreview.visibility = View.VISIBLE
                    binding.audioDim.visibility = View.VISIBLE
                    binding.audioModeIcon.visibility = View.VISIBLE

                    // Загружаем превью
                    if (mode.previewUrl.isNotEmpty()) {
                        binding.audioPreview.load(mode.previewUrl) {
                            crossfade(true)
                            placeholder(R.color.card_bg)
                            error(R.color.card_bg)
                        }
                    }
                }
                is PlaybackModeResolver.Mode.VideoMode -> {
                    isAudioMode = false
                    binding.playerView.visibility = View.VISIBLE
                    binding.audioPreview.visibility = View.GONE
                    binding.audioDim.visibility = View.GONE
                    binding.audioModeIcon.visibility = View.GONE
                }
            }
        }

        val activity = requireActivity() as MainActivity

        binding.btnClose.setOnClickListener {
            if (isFullscreen) {
                exitFullscreen()
                binding.playerView.postDelayed({
                    activity.closePlayer()
                }, 250)
            } else {
                activity.closePlayer()
            }
        }

        binding.btnMinimize.setOnClickListener {
            if (isFullscreen) {
                exitFullscreen()
                binding.playerView.postDelayed({
                    activity.collapseToMini()
                }, 250)
            } else {
                activity.collapseToMini()
            }
        }

        binding.playerRoot.isClickable = true

        setupGestures()
        setupProgressBar()
        setupPlayerControls()
        setupTapAndSwipe()

        binding.controlsLayer.alpha = 0f
        binding.controlsLayer.visibility = View.INVISIBLE
        controlsVisible = false

        applyFullscreenProgressMargins(false)
        applyFullscreenControlsMargins(false)
        binding.progressBar.setCornersEnabled(false)

        binding.progressBar.setExpanded(false)
        binding.thumbView.visibility = View.GONE

        val thumbSizePx = (THUMB_SIZE_DP * resources.displayMetrics.density).toInt()
        val halfThumb = thumbSizePx / 2f

        binding.progressBar.onThumbPosition = { cx, cy ->
            val pbLeft = binding.progressBar.left.toFloat()
            val pbTop = binding.progressBar.top.toFloat()
            binding.thumbView.x = pbLeft + cx - halfThumb
            binding.thumbView.y = pbTop + cy - halfThumb
        }

        binding.progressBar.post {
            binding.progressBar.setProgress(0f)
        }

        loadPaletteFromThumbnail()
    }

    private fun loadPaletteFromThumbnail() {
        val video = requireArguments().getParcelable<Video>(ARG_VIDEO) ?: return
        val url = video.thumbnail
        if (url.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(requireContext())
                        .data(url)
                        .size(80, 80)
                        .allowHardware(false)
                        .build()
                    val result = requireContext().imageLoader.execute(request)
                    (result.drawable as? BitmapDrawable)?.bitmap
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap == null) return@launch

            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).generate()
            }

            val vibrant = palette.getVibrantColor(
                palette.getDominantColor(Color.BLACK)
            )
            val muted = palette.getMutedColor(
                palette.getDarkVibrantColor(Color.BLACK)
            )

            val darkVibrant = blendWithBlack(vibrant, PALETTE_BRIGHTNESS)
            val darkMuted = blendWithBlack(muted, PALETTE_BRIGHTNESS * 0.6f)

            val gradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(darkVibrant, darkMuted)
            )

            val current = (binding.playerFrame.background as? android.graphics.drawable.ColorDrawable)?.color
                ?: Color.BLACK
            val anim = ValueAnimator.ofObject(ArgbEvaluator(), current, darkVibrant).apply {
                duration = 400L
                addUpdateListener { a ->
                    val c = a.animatedValue as Int
                    binding.playerFrame.setBackgroundColor(c)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        binding.playerFrame.background = gradient
                    }
                })
            }
            anim.start()
        }
    }

    private fun blendWithBlack(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    fun hideControlsForStart() {
        handler.removeCallbacks(hideRunnable)
        binding.controlsLayer.animate().cancel()
        binding.controlsLayer.alpha = 0f
        binding.controlsLayer.visibility = View.INVISIBLE
        controlsVisible = false
        binding.progressBar.setExpanded(false)
        binding.thumbView.visibility = View.GONE
    }

    private fun showControls() {
        if (controlsVisible) return
        controlsVisible = true

        handler.removeCallbacks(hideRunnable)
        binding.controlsLayer.animate().cancel()

        binding.controlsLayer.visibility = View.VISIBLE
        binding.controlsLayer.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        if (isFullscreen) {
            binding.progressBar.visibility = View.VISIBLE
        }
        binding.progressBar.setExpanded(true)
        binding.thumbView.visibility = View.VISIBLE

        scheduleHide()
    }

    private fun hideControls() {
        if (!controlsVisible) return
        if (userSeeking) return
        controlsVisible = false

        handler.removeCallbacks(hideRunnable)
        binding.controlsLayer.animate().cancel()

        binding.controlsLayer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                if (!controlsVisible) {
                    binding.controlsLayer.visibility = View.INVISIBLE
                }
            }
            .start()

        binding.progressBar.setExpanded(false)
        binding.thumbView.visibility = View.GONE

        if (isFullscreen) {
            binding.progressBar.visibility = View.INVISIBLE
        }
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private val hideRunnable = Runnable { hideControls() }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 3500)
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isPointInsidePlayerView(e)) return true

                if (rapidSeekActive) {
                    val player = MainActivity.globalPlayer ?: return true
                    val w = binding.playerView.width
                    val half = w / 2
                    val isLeft = e.x < half
                    val deltaMs = if (isLeft) -10_000L else 10_000L

                    val newPos = if (isLeft) {
                        (player.currentPosition - 10_000).coerceAtLeast(0)
                    } else {
                        (player.currentPosition + 10_000).coerceAtMost(player.duration)
                    }
                    player.seekTo(newPos)

                    showSeekEffect(e.x, isLeft, deltaMs)

                    handler.removeCallbacks(rapidSeekResetRunnable)
                    handler.postDelayed(rapidSeekResetRunnable, RAPID_SEEK_WINDOW_MS)

                    scheduleHide()
                    return true
                }

                toggleControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!isPointInsidePlayerView(e)) return false
                val player = MainActivity.globalPlayer ?: return false
                val w = binding.playerView.width
                val half = w / 2
                val isLeft = e.x < half
                val deltaMs = if (isLeft) -10_000L else 10_000L

                val newPos = if (isLeft) {
                    (player.currentPosition - 10_000).coerceAtLeast(0)
                } else {
                    (player.currentPosition + 10_000).coerceAtMost(player.duration)
                }
                player.seekTo(newPos)

                showSeekEffect(e.x, isLeft, deltaMs)

                rapidSeekActive = true
                handler.removeCallbacks(rapidSeekResetRunnable)
                handler.postDelayed(rapidSeekResetRunnable, RAPID_SEEK_WINDOW_MS)

                scheduleHide()
                return true
            }
        })
    }

    private fun showSeekEffect(touchX: Float, isLeft: Boolean, deltaMs: Long) {
        binding.rippleView.start(touchX, binding.rippleView.height / 2f)

        val now = System.currentTimeMillis()
        if (now - lastSeekTapTime > 1000L) {
            accumulatedSeekMs = 0L
        }
        lastSeekTapTime = now
        accumulatedSeekMs += deltaMs

        handler.removeCallbacks(seekResetRunnable)

        val totalSec = (accumulatedSeekMs / 1000.0).roundToInt()
        val prefix = if (totalSec >= 0) "+" else "−"
        val absSec = Math.abs(totalSec)

        binding.seekIndicatorText.text = prefix + absSec + " сек"

        handler.postDelayed(seekResetRunnable, 1000)

        binding.seekIndicator.visibility = View.VISIBLE
        binding.seekIndicator.alpha = 1f
        binding.seekIndicator.animate().cancel()
        binding.seekIndicator.animate()
            .alpha(0f)
            .setStartDelay(700)
            .setDuration(300)
            .withEndAction { binding.seekIndicator.visibility = View.GONE }
            .start()
    }

    private fun showSpeedIndicator() {
        binding.seekIndicatorText.text = "x2"

        binding.seekIndicator.visibility = View.VISIBLE
        binding.seekIndicator.alpha = 1f
        binding.seekIndicator.animate().cancel()

        binding.rippleView.start(
            binding.rippleView.width / 2f,
            binding.rippleView.height / 2f
        )
    }

    private fun startLongPress() {
        handler.postDelayed(longPressRunnable, 500L)
    }

    private fun stopLongPress() {
        handler.removeCallbacks(longPressRunnable)
        if (isLongPressing) {
            isLongPressing = false
            MainActivity.globalPlayer?.setPlaybackSpeed(1.0f)
            binding.seekIndicator.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { binding.seekIndicator.visibility = View.GONE }
                .start()
        }
    }

    private fun setupProgressBar() {
        binding.progressBar.onSeekStart = {
            userSeeking = true
            handler.removeCallbacks(hideRunnable)
            binding.progressBar.setExpanded(true)
            binding.thumbView.visibility = View.VISIBLE
        }
        binding.progressBar.onSeek = { p ->
            val player = MainActivity.globalPlayer
            if (player != null) {
                val duration = player.duration
                if (duration > 0) {
                    val newPos = (duration * p).toLong()
                    player.seekTo(newPos)
                    binding.currentTime.text = formatTime(newPos)
                }
            }
        }
        binding.progressBar.onSeekEnd = {
            userSeeking = false
            if (controlsVisible) {
                scheduleHide()
            } else {
                binding.progressBar.setExpanded(false)
                binding.thumbView.visibility = View.GONE
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapAndSwipe() {
        var touchStartY = 0f
        var touchStartX = 0f
        var movedEnoughForSwipe = false
        var movedUpForFullscreen = false
        var movedBackSwipe = false
        var downInsidePlayer = false

        val backSwipeThresholdPx = (80 * resources.displayMetrics.density)

        binding.playerRoot.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                    touchStartX = event.rawX
                    movedEnoughForSwipe = false
                    movedUpForFullscreen = false
                    movedBackSwipe = false
                    downInsidePlayer = isPointInsidePlayerView(event)

                    if (downInsidePlayer) startLongPress()

                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - touchStartY
                    val dx = event.rawX - touchStartX

                    if (abs(dy) > 20 || abs(dx) > 20) {
                        stopLongPress()
                    }

                    if (isFullscreen && dx > backSwipeThresholdPx && dx > abs(dy) * 1.5f) {
                        movedBackSwipe = true
                        return@setOnTouchListener true
                    }

                    if (dy < -50 && abs(dy) > abs(dx) * 1.5f && !isFullscreen) {
                        movedUpForFullscreen = true
                    }

                    if (dy > 30 && dy > abs(dx) * 1.5f) {
                        movedEnoughForSwipe = true
                        (requireActivity() as MainActivity).onPlayerSwipeDown(dy)
                    }

                    movedEnoughForSwipe || movedUpForFullscreen || movedBackSwipe
                }
                MotionEvent.ACTION_UP -> {
                    stopLongPress()

                    if (movedBackSwipe) {
                        handleBack()
                        true
                    } else if (movedUpForFullscreen && !isFullscreen) {
                        enterFullscreen()
                        true
                    } else if (movedEnoughForSwipe) {
                        val deltaY = event.rawY - touchStartY
                        if (deltaY > 150) {
                            val activity = requireActivity() as MainActivity
                            if (isFullscreen) {
                                exitFullscreen()
                                binding.playerView.postDelayed({
                                    activity.collapseToMini()
                                }, 250)
                            } else {
                                activity.collapseToMini()
                            }
                        } else {
                            (requireActivity() as MainActivity).onPlayerSwipeDown(0f)
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopLongPress()
                    false
                }
                else -> false
            }
        }
    }

    fun handleBack(): Boolean {
        if (isFullscreen) {
            exitFullscreen()
            return true
        }
        return false
    }

    private fun applyFullscreenProgressMargins(fullscreen: Boolean) {
        val lp = binding.progressBar.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val side = if (fullscreen) (FS_SIDE_DP * resources.displayMetrics.density).toInt() else 0
        val bottom = if (fullscreen) (FS_BOTTOM_DP * resources.displayMetrics.density).toInt() else 0
        lp.marginStart = side
        lp.marginEnd = side
        lp.bottomMargin = bottom
        binding.progressBar.layoutParams = lp
        binding.progressBar.requestLayout()
        binding.progressBar.setCornersEnabled(fullscreen)
    }

    private fun applyFullscreenControlsMargins(fullscreen: Boolean) {
        val dm = resources.displayMetrics.density

        val lpTime = binding.timeRow.layoutParams as? ViewGroup.MarginLayoutParams
        if (lpTime != null) {
            lpTime.marginStart = if (fullscreen) (FS_SIDE_DP * dm).toInt()
                              else (NORMAL_SIDE_DP * dm).toInt()
            lpTime.bottomMargin = (NORMAL_BOTTOM_TIME_DP * dm).toInt()
            binding.timeRow.layoutParams = lpTime
            binding.timeRow.requestLayout()
        }

        val lpFs = binding.btnFullscreen.layoutParams as? ViewGroup.MarginLayoutParams
        if (lpFs != null) {
            lpFs.marginEnd = if (fullscreen) (FS_SIDE_DP * dm).toInt()
                             else (NORMAL_END_FS_DP * dm).toInt()
            lpFs.bottomMargin = (NORMAL_BOTTOM_TIME_DP * dm).toInt()
            binding.btnFullscreen.layoutParams = lpFs
            binding.btnFullscreen.requestLayout()
        }
    }

    private fun enterFullscreen() {
        isFullscreen = true
        binding.btnFullscreen.setImageResource(R.drawable.ic_player_exit_fullscreen)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val activity = requireActivity()
        val decor = activity.window.decorView
        decor.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        binding.playerFrame.layoutParams = binding.playerFrame.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.playerView.layoutParams = binding.playerView.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.audioPreview.layoutParams = binding.audioPreview.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.playerFrame.requestLayout()
        binding.playerView.requestLayout()
        binding.audioPreview.requestLayout()

        applyFullscreenProgressMargins(true)
        applyFullscreenControlsMargins(true)

        binding.progressBar.setExpanded(false)
        binding.progressBar.visibility = View.INVISIBLE
        binding.thumbView.visibility = View.GONE

        (requireActivity() as MainActivity).hideContentForFullscreen(true)
        showControls()
    }

    private fun exitFullscreen() {
        isFullscreen = false
        binding.btnFullscreen.setImageResource(R.drawable.ic_player_fullscreen)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val activity = requireActivity()
        val decor = activity.window.decorView
        decor.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        applyFullscreenProgressMargins(false)
        applyFullscreenControlsMargins(false)

        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.setExpanded(false)
        binding.thumbView.visibility = View.GONE

        binding.playerFrame.postDelayed({
            val screenWidth = resources.displayMetrics.widthPixels
            val targetHeight = (screenWidth * 9f / 16f).toInt()
            binding.playerFrame.layoutParams = binding.playerFrame.layoutParams.apply {
                height = targetHeight
            }
            binding.playerView.layoutParams = binding.playerView.layoutParams.apply {
                height = targetHeight
            }
            binding.audioPreview.layoutParams = binding.audioPreview.layoutParams.apply {
                height = targetHeight
            }
            binding.playerFrame.requestLayout()
            binding.playerView.requestLayout()
            binding.audioPreview.requestLayout()
        }, 150)

        (requireActivity() as MainActivity).hideContentForFullscreen(false)
        showControls()
    }

    private fun setupPlayerControls() {
        val player = MainActivity.globalPlayer ?: return

        binding.btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
            scheduleHide()
        }

        binding.btnPrev.setOnClickListener { scheduleHide() }
        binding.btnNext.setOnClickListener { scheduleHide() }
        binding.btnFullscreen.setOnClickListener {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
            scheduleHide()
        }

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_binding == null) return
                binding.btnPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
                )
            }
        }
        player.addListener(playerListener!!)

        val runnable = object : Runnable {
            override fun run() {
                if (_binding != null) {
                    updateTimes()
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(runnable)

        binding.btnPlayPause.setImageResource(
            if (player.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
        )
        updateTimes()
    }

    private fun updateTimes() {
        if (_binding == null) return
        if (userSeeking) return
        val player = MainActivity.globalPlayer ?: return

        val duration = player.duration.coerceAtLeast(0L)
        val position = player.currentPosition.coerceAtLeast(0L)

        binding.currentTime.text = formatTime(position)
        binding.totalTime.text = formatTime(duration)

        if (duration > 0) {
            binding.progressBar.setProgress(position.toFloat() / duration.toFloat())
            binding.progressBar.setBuffered(player.bufferedPosition.toFloat() / duration.toFloat())
        } else {
            binding.progressBar.setProgress(0f)
            binding.progressBar.setBuffered(0f)
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    fun expandForPip() { /* no-op */ }
    fun restoreAfterPip() { /* no-op */ }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        playerListener?.let { MainActivity.globalPlayer?.removeListener(it) }
        playerListener = null
        binding.playerView.player = null
        _binding = null
        super.onDestroyView()
    }
}
