package ru.asmr1.app.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import coil.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.CreatePlaylistRequest
import ru.asmr1.app.data.ProfileVideo
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private lateinit var historyAdapter: HistoryCardAdapter
    private lateinit var playlistAdapter: PlaylistBigAdapter

    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        setupRecyclers()
        setupMenu()
        loadProfile()
    }

    private fun setupRecyclers() {
        historyAdapter = HistoryCardAdapter { pv -> openPlayerFromProfile(pv) }
        binding.historyRecycler.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        binding.historyRecycler.adapter = historyAdapter

        playlistAdapter = PlaylistBigAdapter { card -> openPlaylist(card) }
        binding.playlistsRecycler.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        binding.playlistsRecycler.adapter = playlistAdapter
    }

    private fun setupMenu() {
        binding.historyAll.setOnClickListener {
            Toast.makeText(requireContext(), "Вся история — скоро", Toast.LENGTH_SHORT).show()
        }
        binding.rowSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Настройки — скоро", Toast.LENGTH_SHORT).show()
        }
        binding.rowFaq.setOnClickListener {
            Toast.makeText(requireContext(), "FAQ — скоро", Toast.LENGTH_SHORT).show()
        }
        binding.rowLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }

        binding.btnAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun loadProfile() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token).getMeProfile()
                if (_binding == null) return@launch

                if (response.success) {
                    binding.profileName.text = response.user.name
                    binding.profileEmail.text = response.user.email
                    if (response.user.avatar.isNotEmpty()) {
                        binding.profileAvatar.load(response.user.avatar) {
                            crossfade(true)
                            placeholder(R.color.card_bg)
                            error(R.color.card_bg)
                        }
                    }

                    // ===== История =====
                    if (response.history.isNotEmpty()) {
                        binding.historyWrap.visibility = View.VISIBLE
                        historyAdapter.setItems(response.history)
                    } else {
                        binding.historyWrap.visibility = View.GONE
                    }

                    // ===== Плейлисты: системные + кастомные =====
                    val playlists = mutableListOf<PlaylistCard>()

                    if (response.watchlater.count > 0) {
                        playlists.add(
                            PlaylistCard(
                                id = response.watchlater.playlistId,
                                title = "Смотреть позже",
                                count = response.watchlater.count,
                                type = "watchlater",
                                firstVideo = response.watchlater.firstVideo
                            )
                        )
                    }

                    if (response.liked.count > 0) {
                        playlists.add(
                            PlaylistCard(
                                id = response.liked.playlistId,
                                title = "Понравившиеся",
                                count = response.liked.count,
                                type = "liked",
                                firstVideo = response.liked.firstVideo
                            )
                        )
                    }

                    response.customPlaylists.forEach { custom ->
                        playlists.add(
                            PlaylistCard(
                                id = custom.id,
                                title = custom.title,
                                count = custom.count,
                                type = "custom",
                                firstVideo = custom.firstVideo,
                                visibility = custom.visibility
                            )
                        )
                    }

                    if (playlists.isNotEmpty()) {
                        binding.playlistsWrap.visibility = View.VISIBLE
                        playlistAdapter.setItems(playlists)
                    } else {
                        binding.playlistsWrap.visibility = View.GONE
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ============================================================
    // ДИАЛОГ СОЗДАНИЯ ПЛЕЙЛИСТА
    // ============================================================
    private fun showCreatePlaylistDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.sheet_create_playlist, null)
        dialog.setContentView(sheet)

        val etTitle = sheet.findViewById<EditText>(R.id.et_playlist_title)
        val rgVisibility = sheet.findViewById<RadioGroup>(R.id.rg_visibility)
        val btnCreate = sheet.findViewById<MaterialButton>(R.id.btn_create_playlist)
        val progress = sheet.findViewById<View>(R.id.create_progress)

        btnCreate.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                etTitle.error = "Введите название"
                return@setOnClickListener
            }

            val visibility = if (rgVisibility.checkedRadioButtonId == R.id.rb_private) "private" else "public"

            val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(etTitle.windowToken, 0)

            btnCreate.isEnabled = false
            btnCreate.text = "Создание..."
            progress.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val token = authManager.getToken() ?: run {
                        Toast.makeText(requireContext(), "Нужна авторизация", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        return@launch
                    }
                    val response = ApiClient.createJwtService(token)
                        .createPlaylist(CreatePlaylistRequest(title = title, visibility = visibility))

                    if (response.success && response.playlist != null) {
                        Toast.makeText(requireContext(), "Плейлист создан", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadProfile()
                    } else {
                        Toast.makeText(requireContext(), response.message ?: "Не удалось создать", Toast.LENGTH_SHORT).show()
                        btnCreate.isEnabled = true
                        btnCreate.text = "Создать"
                        progress.visibility = View.GONE
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnCreate.isEnabled = true
                    btnCreate.text = "Создать"
                    progress.visibility = View.GONE
                }
            }
        }

        dialog.show()

        etTitle.requestFocus()
        etTitle.postDelayed({
            val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.showSoftInput(etTitle, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    // ============================================================
    // ОТКРЫТИЕ ПЛЕЙЛИСТА
    // ============================================================
    private fun openPlaylist(card: PlaylistCard) {
        if (card.id <= 0) {
            Toast.makeText(requireContext(), "Плейлист недоступен", Toast.LENGTH_SHORT).show()
            return
        }
        val fragment = PlaylistFragment.newInstance(card.id, card.title, card.type)
        parentFragmentManager.beginTransaction()
            .replace(R.id.feed_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openPlayerFromProfile(pv: ProfileVideo) {
        val video = Video(
            id = pv.id,
            title = pv.title,
            description = "",
            thumbnail = pv.thumbnail,
            videoUrl = pv.videoUrl ?: "",
            audioUrl = pv.audioUrl,
            duration = pv.duration,
            views = pv.views,
            likes = 0,
            published = "",
            author = ru.asmr1.app.data.Author(id = pv.authorId, name = pv.authorName, avatar = ""),
            tags = null
        )
        (activity as? MainActivity)?.openPlayer(video)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }
}
