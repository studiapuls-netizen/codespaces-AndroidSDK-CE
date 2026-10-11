package ru.asmr1.app.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.CreatePlaylistRequest
import ru.asmr1.app.data.PlaylistCardItem
import ru.asmr1.app.databinding.SheetAddToPlaylistBinding

class AddToPlaylistSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_VIDEO_ID = "video_id"
        const val RESULT_KEY = "playlist_added_result"

        fun newInstance(videoId: Int) = AddToPlaylistSheet().apply {
            arguments = Bundle().apply { putInt(ARG_VIDEO_ID, videoId) }
        }
    }

    /** Колбэк после успешного добавления (передаём videoId) */
    var onAdded: ((Int) -> Unit)? = null

    private var _binding: SheetAddToPlaylistBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private lateinit var choiceAdapter: PlaylistChoiceAdapter

    private var videoId: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetAddToPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())
        videoId = requireArguments().getInt(ARG_VIDEO_ID, 0)

        choiceAdapter = PlaylistChoiceAdapter(
            items = emptyList(),
            containsIds = emptySet(),
            onClick = { item -> onPlaylistChosen(item) }
        )
        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylists.adapter = choiceAdapter

        binding.rowCreateNew.setOnClickListener { showCreateForm() }
        binding.btnCreateAndAdd.setOnClickListener { createAndAdd() }

        loadPlaylists()
    }

    // ============================================================
    // ЗАГРУЗКА ПЛЕЙЛИСТОВ + ПРОВЕРКА ГДЕ ЛЕЖИТ ВИДЕО
    // ============================================================
    private fun loadPlaylists() {
        binding.listLoading.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run {
                    binding.listLoading.visibility = View.GONE
                    return@launch
                }
                val api = ApiClient.createJwtService(token)

                val profile = api.getMeProfile()
                val inWhich = try { api.getVideoPlaylists(videoId).playlistIds.toSet() } catch (_: Exception) { emptySet() }

                if (_binding == null) return@launch

                val items = mutableListOf<PlaylistCardItem>()

                if (profile.watchlater.count >= 0) {
                    items.add(
                        PlaylistCardItem(
                            id = profile.watchlater.playlistId,
                            title = "Смотреть позже",
                            count = profile.watchlater.count,
                            type = "watchlater",
                            visibility = "private"
                        )
                    )
                }
                if (profile.liked.count >= 0) {
                    items.add(
                        PlaylistCardItem(
                            id = profile.liked.playlistId,
                            title = "Понравившиеся",
                            count = profile.liked.count,
                            type = "liked",
                            visibility = "private"
                        )
                    )
                }
                profile.customPlaylists.forEach { c ->
                    items.add(
                        PlaylistCardItem(
                            id = c.id,
                            title = c.title,
                            count = c.count,
                            type = "custom",
                            visibility = c.visibility
                        )
                    )
                }

                if (items.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    choiceAdapter.setContainsIds(inWhich)
                    choiceAdapter.setItems(items)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (_binding != null) binding.listLoading.visibility = View.GONE
            }
        }
    }

    // ============================================================
    // ФОРМА СОЗДАНИЯ
    // ============================================================
    private fun showCreateForm() {
        binding.listContainer.visibility = View.GONE
        binding.createForm.visibility = View.VISIBLE
        binding.tvSheetTitle.text = "Новый плейлист"
        binding.etPlaylistTitle.requestFocus()
        binding.etPlaylistTitle.postDelayed({
            val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.showSoftInput(binding.etPlaylistTitle, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    // ============================================================
    // СОЗДАТЬ + ДОБАВИТЬ
    // ============================================================
    private fun createAndAdd() {
        val title = binding.etPlaylistTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.etPlaylistTitle.error = "Введите название"
            return
        }
        val visibility = if (binding.rgVisibility.checkedRadioButtonId == R.id.rb_private) "private" else "public"

        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etPlaylistTitle.windowToken, 0)

        binding.btnCreateAndAdd.isEnabled = false
        binding.btnCreateAndAdd.text = "Создание..."
        binding.createProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run { dismiss(); return@launch }
                val api = ApiClient.createJwtService(token)

                val created = api.createPlaylist(CreatePlaylistRequest(title, visibility))
                if (!created.success || created.playlist == null) {
                    Toast.makeText(requireContext(), created.message ?: "Не удалось создать", Toast.LENGTH_SHORT).show()
                    resetButton()
                    return@launch
                }
                val added = api.addToPlaylist(created.playlist.id, videoId)
                if (_binding == null) return@launch

                if (added.success) {
                    Toast.makeText(requireContext(), "Добавлено в «$title»", Toast.LENGTH_SHORT).show()
                    onAdded?.invoke(videoId)
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), added.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            }
        }
    }

    private fun resetButton() {
        _binding?.btnCreateAndAdd?.isEnabled = true
        _binding?.btnCreateAndAdd?.text = "Создать и добавить"
        _binding?.createProgress?.visibility = View.GONE
    }

    // ============================================================
    // ВЫБОР СУЩЕСТВУЮЩЕГО ПЛЕЙЛИСТА
    // ============================================================
    private fun onPlaylistChosen(item: PlaylistCardItem) {
        if (item.type == "liked") {
            Toast.makeText(requireContext(), "Понравившиеся формируются лайками", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run { dismiss(); return@launch }
                val response = ApiClient.createJwtService(token)
                    .addToPlaylist(item.id, videoId)
                if (_binding == null) return@launch

                if (response.success) {
                    if (response.alreadyIn) {
                        Toast.makeText(requireContext(), "Уже в «${item.title}»", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Добавлено в «${item.title}»", Toast.LENGTH_SHORT).show()
                    }
                    onAdded?.invoke(videoId)
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
