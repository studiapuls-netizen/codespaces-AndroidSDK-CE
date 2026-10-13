package ru.asmr1.app

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.auth.LoginFragment
import ru.asmr1.app.auth.RegisterFragment
import ru.asmr1.app.data.Video
import ru.asmr1.app.data.Comment
import ru.asmr1.app.download.PlaybackModeResolver
import ru.asmr1.app.player.AudioBoost
import ru.asmr1.app.player.CommentsFragment
import ru.asmr1.app.player.ContentFragment
import ru.asmr1.app.player.DescriptionFragment
import ru.asmr1.app.player.PlayerFragment
import ru.asmr1.app.player.RippleView
import ru.asmr1.app.profile.ProfileFragment
import ru.asmr1.app.ui.FeedFragment
import ru.asmr1.app.ui.StubFragment
import kotlin.math.abs
import kotlin.math.hypot

// ===== Top-level константы (видны extension-функциям) =====
internal const val CARD_RATIO = 9f / 16f
internal const val CONTROLS_DP = 36f
internal const val CONTROLS_MARGIN_DP = 2f

class MainActivity : AppCompatActivity() {

    companion object {
        var globalPlayer: ExoPlayer? = null
        var currentVideo: Video? = null
        val audioBoost = AudioBoost()

        private const val SPLASH_DURATION_MS = 2000L
        private const val SPLASH_FADE_MS = 400L
        private const val SPLASH_RIPPLE_INTERVAL_MS = 1100L

        const val TAG_LOGIN = "login_fragment"
        const val TAG_REGISTER = "register_fragment"
    }

    // ===== Поля, доступные extension-функциям =====

    internal lateinit var feedContainer: View
    internal lateinit var authorContainer: View
    internal lateinit var overlayContainer: View
    internal lateinit var playerContainer: View
    internal lateinit var contentContainer: View
    internal lateinit var contentOverlayContainer: View
    internal lateinit var descriptionOverlayContainer: View
    internal lateinit var commentsOverlayContainer: View
    internal lateinit var miniPlayerWrap: View
    internal lateinit var miniPlayerContainer: View
    internal lateinit var miniControls: View
    internal lateinit var miniPlayerView: PlayerView
    internal lateinit var miniBtnPrev: ImageButton
    internal lateinit var miniBtnPlayPause: ImageButton
    internal lateinit var miniBtnNext: ImageButton
    internal lateinit var miniBtnClose: ImageButton
    lateinit var bottomNav: BottomNavigationView
        private set

    internal lateinit var authManager: AuthManager

    internal val density by lazy { resources.displayMetrics.density }
    internal val marginPx by lazy { (12 * density).toInt() }

    internal val screenHeightPx: Float
        get() = resources.displayMetrics.heightPixels.toFloat()

    internal val minWidthDp = 100
    internal val maxWidthDp = 400

    internal var miniPlayerRestoreW = 0
    internal var miniPlayerRestoreH = 0
    internal var miniPlayerRestoreX = 0f
    internal var miniPlayerRestoreY = 0f

    internal var wasInPip = false
    internal var pendingRestoreModal = false

    internal var isAnimatingCollapse = false
    internal var pendingExpandAnimation = false

    internal var lastAudioSessionId = 0
    internal var pendingCommentFocus: Comment? = null
    internal val splashHandler = Handler(Looper.getMainLooper())
    internal var splashRippleRun: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authManager = AuthManager(this)

        feedContainer = findViewById(R.id.feed_container)
        authorContainer = findViewById(R.id.author_container)
        overlayContainer = findViewById(R.id.overlay_container)
        playerContainer = findViewById(R.id.player_container)
        contentContainer = findViewById(R.id.content_container)
        contentOverlayContainer = findViewById(R.id.content_overlay_container)
        descriptionOverlayContainer = findViewById(R.id.description_overlay_container)
        commentsOverlayContainer = findViewById(R.id.comments_overlay_container)
        miniPlayerWrap = findViewById(R.id.mini_wrap)
        miniPlayerContainer = findViewById(R.id.mini_player_container)
        miniControls = findViewById(R.id.mini_controls)
        miniPlayerView = findViewById(R.id.mini_player_view)
        miniBtnClose = findViewById(R.id.mini_btn_close)
        miniBtnPrev = findViewById(R.id.mini_btn_prev)
        miniBtnPlayPause = findViewById(R.id.mini_btn_play_pause)
        miniBtnNext = findViewById(R.id.mini_btn_next)
        bottomNav = findViewById(R.id.bottom_nav)

        val sw = resources.displayMetrics.widthPixels
        val overlayTopMarginPx = (sw * 9f / 16f).toInt()
        val mlp = contentOverlayContainer.layoutParams as? ViewGroup.MarginLayoutParams
        mlp?.topMargin = overlayTopMarginPx
        contentOverlayContainer.layoutParams = mlp

        val dmlp = descriptionOverlayContainer.layoutParams as? ViewGroup.MarginLayoutParams
        dmlp?.topMargin = overlayTopMarginPx
        descriptionOverlayContainer.layoutParams = dmlp

        val cmlp = commentsOverlayContainer.layoutParams as? ViewGroup.MarginLayoutParams
        cmlp?.topMargin = overlayTopMarginPx
        commentsOverlayContainer.layoutParams = cmlp

        if (globalPlayer == null) {
            globalPlayer = ExoPlayer.Builder(this).build().also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) closePlayer()
                        if (state == Player.STATE_READY) attachAudioBoostIfNeeded()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (::miniBtnPlayPause.isInitialized) {
                            miniBtnPlayPause.setImageResource(
                                if (isPlaying) R.drawable.ic_player_pause
                                else R.drawable.ic_player_play
                            )
                        }
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (audioSessionId != 0 && audioSessionId != lastAudioSessionId) {
                            lastAudioSessionId = audioSessionId
                            audioBoost.attach(audioSessionId)
                            audioBoost.fadeIn()
                        }
                    }
                })
            }
        }

        miniBtnClose.setOnClickListener { closePlayer() }
        miniBtnPlayPause.setOnClickListener {
            val p = globalPlayer ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
        }
        miniBtnPrev.setOnClickListener { /* TODO */ }
        miniBtnNext.setOnClickListener { /* TODO */ }

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    if (f is PlayerFragment && pendingExpandAnimation) {
                        pendingExpandAnimation = false
                        v.post { runExpandAnimation() }
                    }
                }
            },
            false
        )

        setupMiniPlayerGestures()
        setupBottomNav(savedInstanceState)
        setupSplash()

        if (savedInstanceState == null && !authManager.isLoggedIn()) {
            showLoginScreen()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Страница автора — закрываем первой
                if (isAuthorPageOpen()) {
                    closeAuthorPage()
                    return
                }
                val loginFrag = supportFragmentManager.findFragmentByTag(TAG_LOGIN)
                if (loginFrag != null && loginFrag.isVisible) {
                    supportFragmentManager.popBackStack()
                    return
                }
                val registerFrag = supportFragmentManager.findFragmentByTag(TAG_REGISTER)
                if (registerFrag != null && registerFrag.isVisible) {
                    supportFragmentManager.popBackStack()
                    return
                }

                if (commentsOverlayContainer.visibility == View.VISIBLE) {
                    closeComments()
                    return
                }
                if (descriptionOverlayContainer.visibility == View.VISIBLE) {
                    closeDescription()
                    return
                }

                val pf = getPlayerFragmentOrNull()
                if (pf != null && pf.handleBack()) return

                if (currentVideo != null && overlayContainer.visibility == View.VISIBLE && miniPlayerWrap.visibility != View.VISIBLE) {
                    collapseToMini()
                    return
                }
                if (currentVideo != null && miniPlayerWrap.visibility == View.VISIBLE) {
                    closePlayer()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    // ============================================================
    // АВТОРИЗАЦИЯ
    // ============================================================

    fun showLoginScreen() {
        supportFragmentManager.popBackStack(TAG_LOGIN, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        if (supportFragmentManager.findFragmentByTag(TAG_LOGIN) != null) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.overlay_container, LoginFragment(), TAG_LOGIN)
            .addToBackStack(null)
            .commit()
        overlayContainer.visibility = View.VISIBLE
    }

    fun showRegisterScreen() {
        supportFragmentManager.popBackStack(TAG_REGISTER, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        if (supportFragmentManager.findFragmentByTag(TAG_REGISTER) != null) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.overlay_container, RegisterFragment(), TAG_REGISTER)
            .addToBackStack(null)
            .commit()
        overlayContainer.visibility = View.VISIBLE
    }

    fun onLoginSuccess() {
        supportFragmentManager.popBackStack(TAG_LOGIN, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.popBackStack(TAG_REGISTER, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        if (currentVideo == null && miniPlayerWrap.visibility != View.VISIBLE) {
            overlayContainer.visibility = View.GONE
        }
        Toast.makeText(this, "Вы вошли", Toast.LENGTH_SHORT).show()
    }

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    fun logout() {
        authManager.clear()

        supportFragmentManager.popBackStack(TAG_LOGIN, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.popBackStack(TAG_REGISTER, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show()

        supportFragmentManager.beginTransaction()
            .replace(R.id.feed_container, FeedFragment())
            .commit()
        bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true

        showLoginScreen()
    }

    // ============================================================
    // SPLASH
    // ============================================================

    private fun setupSplash() {
        val splashOverlay = findViewById<View>(R.id.splash_overlay)
        val splashRipple = findViewById<RippleView>(R.id.splash_ripple)
        val splashLogo = findViewById<ImageView>(R.id.splash_logo)

        splashLogo.scaleX = 0.7f
        splashLogo.scaleY = 0.7f
        splashLogo.alpha = 0f
        splashLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .start()

        val rippleRun = object : Runnable {
            override fun run() {
                if (splashRipple.width > 0) {
                    splashRipple.start(
                        splashRipple.width / 2f,
                        splashRipple.height / 2f
                    )
                }
                splashHandler.postDelayed(this, SPLASH_RIPPLE_INTERVAL_MS)
            }
        }
        splashRippleRun = rippleRun
        splashRipple.post { rippleRun.run() }

        splashHandler.postDelayed({
            splashRippleRun?.let { splashHandler.removeCallbacks(it) }
            splashOverlay.animate()
                .alpha(0f)
                .withLayer()
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { splashOverlay.visibility = View.GONE }
                .start()
        }, SPLASH_DURATION_MS)
    }

    private fun attachAudioBoostIfNeeded() {
        val p = globalPlayer ?: return
        val id = p.audioSessionId
        if (id == 0) return
        if (id == lastAudioSessionId) return
        lastAudioSessionId = id
        audioBoost.attach(id)
        audioBoost.fadeIn()
    }

    // ============================================================
    // BOTTOM NAV
    // ============================================================

    private fun setupBottomNav(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.feed_container, FeedFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            try {
                // Если открыта страница автора — закрываем её
                if (isAuthorPageOpen()) {
                    closeAuthorPage()
                }
                when (item.itemId) {
                    R.id.nav_home -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.feed_container, FeedFragment())
                            .commit()
                        true
                    }
                    R.id.nav_search -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.feed_container, ru.asmr1.app.ui.SearchFragment())
                            .commit()
                        true
                    }
                    R.id.nav_subs -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.feed_container, ru.asmr1.app.ui.SubscriptionsFragment())
                            .commit()
                        true
                    }
                    R.id.nav_bell -> {
                        if (authManager.isLoggedIn()) {
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.feed_container, ru.asmr1.app.ui.NotificationsFragment())
                                .commit()
                        } else {
                            showLoginScreen()
                        }
                        true
                    }
                    R.id.nav_profile -> {
                        if (authManager.isLoggedIn()) {
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.feed_container, ProfileFragment())
                                .commit()
                        } else {
                            showLoginScreen()
                        }
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "CRASH: ${e.javaClass.simpleName}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }


    // ============================================================
    // СТРАНИЦА АВТОРА (как фрагмент поверх feed_container)
    // ============================================================

    fun openAuthorPage(authorId: Int) {
        if (authorId <= 0) return
        authorContainer.visibility = View.VISIBLE
        authorContainer.translationY = screenHeightPx
        authorContainer.alpha = 1f
        supportFragmentManager.beginTransaction()
            .replace(R.id.author_container, ru.asmr1.app.author.AuthorPageFragment.newInstance(authorId))
            .commit()
        authorContainer.post {
            authorContainer.animate()
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun closeAuthorPage() {
        authorContainer.animate()
            .translationY(screenHeightPx)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                supportFragmentManager.findFragmentById(R.id.author_container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                authorContainer.visibility = View.GONE
                authorContainer.translationY = 0f
            }
            .start()
    }

    internal fun isAuthorPageOpen(): Boolean {
        return ::authorContainer.isInitialized && authorContainer.visibility == View.VISIBLE
    }

    // ============================================================
    // PLAYER
    // ============================================================

    internal fun getPlayerFragmentOrNull(): PlayerFragment? {
        return supportFragmentManager
            .findFragmentById(R.id.player_container) as? PlayerFragment
    }

    internal fun getContentFragmentOrNull(): ContentFragment? {
        return supportFragmentManager
            .findFragmentById(R.id.content_overlay_container) as? ContentFragment
    }

    internal fun openPlayerFragments(video: Video) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.player_container, PlayerFragment.newInstance(video))
            .replace(R.id.content_overlay_container, ContentFragment.newInstance(video))
            .commit()
    }

    internal fun removePlayerFragments() {
        supportFragmentManager.findFragmentById(R.id.player_container)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        supportFragmentManager.findFragmentById(R.id.content_overlay_container)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
    }

    fun openPlayerWithTransition(video: Video, sourceRect: Rect, thumbUrl: String) {
        openPlayer(video)
    }

    fun openPlayer(video: Video) {
        currentVideo = video

        val mode = PlaybackModeResolver.resolve(this, video)
        val playUri = when (mode) {
            is PlaybackModeResolver.Mode.VideoMode -> mode.uri
            is PlaybackModeResolver.Mode.AudioMode -> mode.uri
        }

        globalPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(playUri))
            prepare()
            playWhenReady = true
        }

        overlayContainer.visibility = View.VISIBLE
        overlayContainer.translationY = screenHeightPx
        overlayContainer.alpha = 1f

        playerContainer.visibility = View.VISIBLE
        contentContainer.visibility = View.VISIBLE
        contentOverlayContainer.visibility = View.VISIBLE
        descriptionOverlayContainer.visibility = View.GONE
        commentsOverlayContainer.visibility = View.GONE
        miniPlayerWrap.visibility = View.GONE
        miniPlayerView.player = null

        playerContainer.translationY = 0f
        playerContainer.alpha = 1f
        contentOverlayContainer.translationY = 0f
        contentOverlayContainer.alpha = 1f

        openPlayerFragments(video)

        overlayContainer.post {
            overlayContainer.animate()
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        enablePip()
    }

    fun openDescription(video: Video) {
        descriptionOverlayContainer.visibility = View.VISIBLE
        descriptionOverlayContainer.translationY = screenHeightPx
        descriptionOverlayContainer.alpha = 1f

        supportFragmentManager.beginTransaction()
            .replace(R.id.description_overlay_container, DescriptionFragment.newInstance(video))
            .commit()

        descriptionOverlayContainer.post {
            descriptionOverlayContainer.animate()
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun closeDescription() {
        descriptionOverlayContainer.animate()
            .translationY(screenHeightPx)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                supportFragmentManager.findFragmentById(R.id.description_overlay_container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                descriptionOverlayContainer.visibility = View.GONE
                descriptionOverlayContainer.translationY = 0f
            }
            .start()
    }

    fun openComments(video: Video) {
        commentsOverlayContainer.visibility = View.VISIBLE
        commentsOverlayContainer.translationY = screenHeightPx
        commentsOverlayContainer.alpha = 1f

        supportFragmentManager.beginTransaction()
            .replace(R.id.comments_overlay_container, CommentsFragment.newInstance(video))
            .commit()

        commentsOverlayContainer.post {
            commentsOverlayContainer.animate()
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun openCommentsWithFocus(video: Video, focusComment: Comment) {
        pendingCommentFocus = focusComment
        openComments(video)
    }
    fun closeComments() {
        commentsOverlayContainer.animate()
            .translationY(screenHeightPx)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                supportFragmentManager.findFragmentById(R.id.comments_overlay_container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                commentsOverlayContainer.visibility = View.GONE
                commentsOverlayContainer.translationY = 0f
            }
            .start()
    }

    fun hideContentForFullscreen(hide: Boolean) {
        contentContainer.visibility = if (hide) View.GONE else View.VISIBLE
        contentOverlayContainer.visibility = if (hide) View.GONE else View.VISIBLE
    }

    fun onPlayerSwipeDown(deltaY: Float) {
        val pf = getPlayerFragmentOrNull()
        pf?.view?.translationY = deltaY
        contentOverlayContainer.translationY = deltaY
        contentOverlayContainer.alpha = 1f - (deltaY / 800f).coerceIn(0f, 1f)
    }

    fun collapseToMini() {
        if (currentVideo == null || globalPlayer == null) return
        if (isAnimatingCollapse) return
        isAnimatingCollapse = true

        descriptionOverlayContainer.visibility = View.GONE
        commentsOverlayContainer.visibility = View.GONE

        val pf = getPlayerFragmentOrNull()
        val playerFrame = pf?.view?.findViewById<View>(R.id.player_frame)
        val playerRoot = pf?.view
        val contentView = getContentFragmentOrNull()?.view

        playerRoot?.animate()?.cancel()
        playerFrame?.animate()?.cancel()
        contentView?.animate()?.cancel()

        val targetW = (200 * density).toInt()
        val targetWrapH = wrapHeightForWidth(targetW)

        val overlayW = overlayContainer.width
        val overlayH = overlayContainer.height
        val targetX = (overlayW - targetW - marginPx).toFloat()
        val targetY = (overlayH - targetWrapH - marginPx).toFloat()

        val pfLoc = IntArray(2)
        playerFrame?.getLocationInWindow(pfLoc)
        val ovLoc = IntArray(2)
        overlayContainer.getLocationInWindow(ovLoc)
        val frameX = (pfLoc[0] - ovLoc[0]).toFloat()
        val frameY = (pfLoc[1] - ovLoc[1]).toFloat()
        val frameW = (playerFrame?.width ?: 1).toFloat().coerceAtLeast(1f)
        val frameH = (playerFrame?.height ?: 1).toFloat().coerceAtLeast(1f)

        val targetCardH = (targetW * CARD_RATIO).toInt()
        val scaleX = targetW / frameW
        val scaleY = targetCardH / frameH
        val dx = targetX - frameX - (frameW * (1 - scaleX) / 2f)
        val dy = targetY - frameY - (frameH * (1 - scaleY) / 2f)

        miniPlayerView.player = globalPlayer
        val lp = miniPlayerWrap.layoutParams
        lp.width = targetW
        lp.height = targetWrapH
        miniPlayerWrap.layoutParams = lp
        miniPlayerWrap.x = targetX
        miniPlayerWrap.y = targetY
        miniPlayerWrap.alpha = 0f
        miniPlayerWrap.visibility = View.VISIBLE

        playerFrame?.animate()
            ?.scaleX(scaleX)
            ?.scaleY(scaleY)
            ?.translationX(dx)
            ?.translationY(dy)
            ?.setDuration(300)
            ?.withEndAction {
                miniPlayerWrap.alpha = 1f
                playerRoot?.visibility = View.GONE
                playerFrame?.animate()?.cancel()
                playerFrame?.scaleX = 1f
                playerFrame?.scaleY = 1f
                playerFrame?.translationX = 0f
                playerFrame?.translationY = 0f
                playerRoot?.translationY = 0f

                removePlayerFragments()
                supportFragmentManager.findFragmentById(R.id.description_overlay_container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                supportFragmentManager.findFragmentById(R.id.comments_overlay_container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }

                playerContainer.visibility = View.GONE
                contentContainer.visibility = View.GONE
                overlayContainer.visibility = View.VISIBLE
                overlayContainer.translationY = 0f

                feedContainer.visibility = View.VISIBLE
                isAnimatingCollapse = false

                bottomNav.visibility = View.VISIBLE

                refreshPipSourceRect()
            }
            ?.start()

        contentView?.animate()
            ?.translationY((contentView.height).toFloat())
            ?.alpha(0f)
            ?.setDuration(300)
            ?.start()
    }

    fun expandFromMini() {
        val video = currentVideo ?: return

        playerContainer.animate().cancel()
        contentOverlayContainer.animate().cancel()

        overlayContainer.visibility = View.VISIBLE
        playerContainer.visibility = View.VISIBLE
        contentContainer.visibility = View.VISIBLE
        contentOverlayContainer.visibility = View.VISIBLE

        playerContainer.translationY = screenHeightPx
        playerContainer.alpha = 0f
        contentOverlayContainer.translationY = screenHeightPx
        contentOverlayContainer.alpha = 0f

        miniPlayerWrap.visibility = View.GONE
        miniPlayerView.player = null

        pendingExpandAnimation = true
        openPlayerFragments(video)
    }

    internal fun runExpandAnimation() {
        playerContainer.animate().cancel()
        contentOverlayContainer.animate().cancel()

        playerContainer.translationY = screenHeightPx
        playerContainer.alpha = 0f
        contentOverlayContainer.translationY = screenHeightPx
        contentOverlayContainer.alpha = 0f

        val decel = DecelerateInterpolator()

        playerContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(320)
            .setInterpolator(decel)
            .start()

        contentOverlayContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(320)
            .setInterpolator(decel)
            .start()

        enablePip()
    }

    fun closePlayer() {
        if (descriptionOverlayContainer.visibility == View.VISIBLE) {
            supportFragmentManager.findFragmentById(R.id.description_overlay_container)?.let {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
            descriptionOverlayContainer.visibility = View.GONE
        }
        if (commentsOverlayContainer.visibility == View.VISIBLE) {
            supportFragmentManager.findFragmentById(R.id.comments_overlay_container)?.let {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
            commentsOverlayContainer.visibility = View.GONE
        }

        globalPlayer?.stop()
        globalPlayer?.clearMediaItems()
        currentVideo = null

        audioBoost.release()
        lastAudioSessionId = 0

        miniPlayerWrap.visibility = View.GONE
        miniPlayerView.player = null
        overlayContainer.visibility = View.GONE
        feedContainer.visibility = View.VISIBLE

        bottomNav.visibility = View.VISIBLE

        // Если страница автора была открыта — показываем снова
        if (::authorContainer.isInitialized && 
            supportFragmentManager.findFragmentById(R.id.author_container) != null) {
            authorContainer.visibility = View.VISIBLE
        }

        disablePip()
    }

    fun setOverlayOffset(offset: Float) {
        overlayContainer.translationY = offset
    }

    // ============================================================
    // PIP
    // ============================================================

    internal fun enablePip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(true)
            if (miniPlayerWrap.visibility == View.VISIBLE &&
                miniPlayerContainer.width > 0 && miniPlayerContainer.height > 0) {
                val rect = Rect()
                miniPlayerContainer.getGlobalVisibleRect(rect)
                if (!rect.isEmpty) builder.setSourceRectHint(rect)
            }
            setPictureInPictureParams(builder.build())
        } catch (_: Exception) {}
    }

    internal fun disablePip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            setPictureInPictureParams(
                PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
            )
        } catch (_: Exception) {}
    }

    internal fun refreshPipSourceRect() {
        if (miniPlayerWrap.visibility != View.VISIBLE) return
        if (miniPlayerContainer.width == 0 || miniPlayerContainer.height == 0) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val rect = Rect()
            miniPlayerContainer.getGlobalVisibleRect(rect)
            if (rect.isEmpty) return
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(true)
                    .setSourceRectHint(rect)
                    .build()
            )
        } catch (_: Exception) {}
    }

    internal fun forceResetMiniPlayer() {
        if (miniPlayerRestoreW > 0) {
            val lp = miniPlayerWrap.layoutParams
            lp.width = miniPlayerRestoreW
            lp.height = miniPlayerRestoreH
            miniPlayerWrap.layoutParams = lp
            miniPlayerWrap.x = miniPlayerRestoreX
            miniPlayerWrap.y = miniPlayerRestoreY
        } else {
            val lp = miniPlayerWrap.layoutParams
            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT ||
                lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                val w = (200 * density).toInt()
                lp.width = w
                lp.height = wrapHeightForWidth(w)
                miniPlayerWrap.layoutParams = lp
            }
        }
        miniPlayerRestoreW = 0
        miniPlayerRestoreH = 0
        miniPlayerWrap.animate().cancel()
        miniPlayerWrap.visibility = View.GONE
        miniPlayerWrap.alpha = 0f
        miniPlayerView.player = null
    }

    internal fun restoreBigPlayerPaused() {
        if (currentVideo == null) return

        forceResetMiniPlayer()
        getPlayerFragmentOrNull()?.restoreAfterPip()

        feedContainer.visibility = View.VISIBLE
        overlayContainer.visibility = View.VISIBLE
        overlayContainer.translationY = 0f
        playerContainer.visibility = View.VISIBLE
        contentContainer.visibility = View.VISIBLE
        contentOverlayContainer.visibility = View.VISIBLE
        playerContainer.translationY = 0f
        playerContainer.alpha = 1f
        contentOverlayContainer.translationY = 0f
        contentOverlayContainer.alpha = 1f

        val hasPlayerFragment = supportFragmentManager
            .findFragmentById(R.id.player_container) is PlayerFragment

        if (!hasPlayerFragment) openPlayerFragments(currentVideo!!)
        val pvF = getPlayerFragmentOrNull()?.view?.findViewById<PlayerView>(R.id.player_view)
        pvF?.player = null
        pvF?.player = globalPlayer

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        playerContainer.post {
            setBigPlayerExpandedForPip(expand = false)
            playerContainer.post {
                setBigPlayerExpandedForPip(expand = false)
            }
        }

        enablePip()
    }

    internal fun setBigPlayerExpandedForPip(expand: Boolean) {
        val pf = getPlayerFragmentOrNull() ?: return
        val root = pf.view ?: return
        val frame = root.findViewById<FrameLayout>(R.id.player_frame) ?: return
        val pv = root.findViewById<PlayerView>(R.id.player_view) ?: return

        val dm = resources.displayMetrics
        val screenWidth = minOf(dm.widthPixels, dm.heightPixels)
        val targetHeight = (screenWidth * 9f / 16f).toInt()

        if (expand) {
            frame.layoutParams = frame.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            pv.layoutParams = pv.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        } else {
            frame.layoutParams = frame.layoutParams.apply {
                height = targetHeight
            }
            pv.layoutParams = pv.layoutParams.apply { height = targetHeight }
        }
        frame.requestLayout()
        pv.requestLayout()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            wasInPip = true
            feedContainer.visibility = View.GONE
            contentContainer.visibility = View.GONE
            contentOverlayContainer.visibility = View.GONE
            descriptionOverlayContainer.visibility = View.GONE
            commentsOverlayContainer.visibility = View.GONE

            miniControls.visibility = View.GONE

            setBigPlayerExpandedForPip(expand = true)

            if (miniPlayerWrap.visibility == View.VISIBLE) {
                miniPlayerRestoreW = miniPlayerWrap.width
                miniPlayerRestoreH = miniPlayerWrap.height
                miniPlayerRestoreX = miniPlayerWrap.x
                miniPlayerRestoreY = miniPlayerWrap.y

                miniPlayerWrap.animate().cancel()
                miniPlayerWrap.alpha = 1f

                val lp = miniPlayerWrap.layoutParams
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                miniPlayerWrap.layoutParams = lp
                miniPlayerWrap.x = 0f
                miniPlayerWrap.y = 0f
            }
        } else {
            if (!wasInPip) return
            wasInPip = false

            playerContainer.visibility = View.INVISIBLE
            contentOverlayContainer.visibility = View.INVISIBLE

            miniControls.visibility = View.VISIBLE

            setBigPlayerExpandedForPip(expand = false)

            forceResetMiniPlayer()

            pendingRestoreModal = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (pendingRestoreModal) {
                    pendingRestoreModal = false
                    restoreBigPlayerPaused()
                    enablePip()
                }
            }, 50)
        }
    }

    override fun onStop() {
        super.onStop()
        if (wasInPip) {
            wasInPip = false
            pendingRestoreModal = true
        }
        if (miniPlayerRestoreW > 0 && miniPlayerWrap.visibility == View.VISIBLE) {
            val lp = miniPlayerWrap.layoutParams
            lp.width = miniPlayerRestoreW
            lp.height = miniPlayerRestoreH
            miniPlayerWrap.layoutParams = lp
            miniPlayerWrap.x = miniPlayerRestoreX
            miniPlayerWrap.y = miniPlayerRestoreY
        }
        miniPlayerRestoreW = 0
        miniPlayerRestoreH = 0
    }

    override fun onStart() {
        super.onStart()
        if (pendingRestoreModal) {
            pendingRestoreModal = false
            if (currentVideo != null) {
                window.decorView.post {
                    restoreBigPlayerPaused()
                    enablePip()
                }
            }
        }
    }
    override fun onDestroy() {

        super.onDestroy()
        splashRippleRun?.let { splashHandler.removeCallbacks(it) }
        splashHandler.removeCallbacksAndMessages(null)
        if (isFinishing) {
            audioBoost.release()
            globalPlayer?.release()
            globalPlayer = null
            currentVideo = null        }
    }
}
