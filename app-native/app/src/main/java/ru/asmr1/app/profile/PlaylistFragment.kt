package ru.asmr1.app.profile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.Author
import ru.asmr1.app.data.ProfileVideo
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentPlaylistBinding

class PlaylistFragment : Fragment() {

    companion object {
        private const val ARG_PLAYLIST_ID = "playlist_id"
        private const val ARG_PLAYLIST_TITLE = "playlist_title"
        private const val ARG_PLAYLIST_TYPE = "playlist_type"

        fun newInstance(playlistId: Int, title: String, type: String) = PlaylistFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_PLAYLIST_ID, playlistId)
                putString(ARG_PLAYLIST_TITLE, title)
                putString(ARG_PLAYLIST_TYPE, type)
            }
        }
    }

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private lateinit var adapter: PlaylistVideoAdapter

    private var playlistId: Int = 0
    private var playlistTitle: String = ""
    private var playlistType: String = "custom"
    private var playlistVisibility: String = "public"
    private var canEdit: Boolean = true

    private var loadJob: Job? = null
    private var deleteJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        playlistId = requireArguments().getInt(ARG_PLAYLIST_ID, 0)
        playlistTitle = requireArguments().getString(ARG_PLAYLIST_TITLE) ?: ""
        playlistType = requireArguments().getString(ARG_PLAYLIST_TYPE) ?: "custom"

        binding.playlistTitle.text = playlistTitle
        binding.playlistCount.text = ""

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Иконку редактирования показываем только для custom (не для liked/watchlater)
        binding.btnEditPlaylist.visibility = View.GONE // скрыть до загрузки
        binding.btnEditPlaylist.setOnClickListener { openEditSheet() }

        setupRecycler()
        loadPlaylist()
    }

    private fun setupRecycler() {
        adapter = PlaylistVideoAdapter(
            items = mutableListOf(),
            canEdit = true,
            onClick = { pv -> openPlayerFromPlaylist(pv) },
            onDeleteSwipe = { pv, _ -> onSwipeDelete(pv) },
            onReorder = { list -> onSaveOrder(list) }
        )

        binding.playlistRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.playlistRecycler.adapter = adapter
        binding.playlistRecycler.setHasFixedSize(false)
        adapter.attachToRecyclerView(binding.playlistRecycler)
    }

    private fun loadPlaylist() {
        loadJob?.cancel()
        showLoading(true)

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run {
                    showError("Нужна авторизация")
                    return@launch
                }

                val response = ApiClient.createJwtService(token).getPlaylist(playlistId)
                if (_binding == null) return@launch

                if (response.success) {
                    binding.playlistTitle.text = response.playlist.title
                    playlistTitle = response.playlist.title
                    playlistType = response.playlist.type
                    canEdit = response.playlist.canEdit
                    playlistVisibility = response.playlist.visibility ?: "public"

                    updateCount(response.videos.size)

                    // Иконку редактирования показываем только для custom
                    binding.btnEditPlaylist.visibility =
                        if (playlistType == "custom") View.VISIBLE else View.GONE

                    adapter = PlaylistVideoAdapter(
                        items = response.videos.toMutableList(),
                        canEdit = response.playlist.canEdit,
                        onClick = { pv -> openPlayerFromPlaylist(pv) },
                        onDeleteSwipe = { pv, _ -> onSwipeDelete(pv) },
                        onReorder = { list -> onSaveOrder(list) }
                    )
                    binding.playlistRecycler.adapter = adapter
                    adapter.attachToRecyclerView(binding.playlistRecycler)

                    if (response.videos.isEmpty()) showEmpty(true) else showEmpty(false)
                } else {
                    showError("Не удалось загрузить плейлист")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PlaylistFragment", "loadPlaylist error", e)
                if (_binding != null) showError("Ошибка: ${e.message}")
            } finally {
                if (_binding != null) showLoading(false)
            }
        }
    }

    // ============================================================
    // РЕДАКТИРОВАНИЕ
    // ============================================================
    private fun openEditSheet() {
        val sheet = EditPlaylistSheet.newInstance(playlistId, playlistTitle, playlistVisibility)

        sheet.onSaved = { _, newTitle, newVisibility ->
            playlistTitle = newTitle
            playlistVisibility = newVisibility
            _binding?.playlistTitle?.text = newTitle
        }

        sheet.onDeleted = { _ ->
            // Возвращаемся в профиль
            parentFragmentManager.popBackStack()
        }

        sheet.show(childFragmentManager, "edit_playlist")
    }

    // ============================================================
    // УДАЛЕНИЕ ВИДЕО
    // ============================================================
    private fun onSwipeDelete(video: ProfileVideo) {
        val current = adapter.getItems().toMutableList()
        current.removeAll { it.id == video.id }
        adapter.setItems(current)
        updateCount(current.size)
        if (current.isEmpty()) showEmpty(true)

        deleteJob?.cancel()
        deleteJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val response = ApiClient.createJwtService(token)
                    .removeFromPlaylist(playlistId, video.id)

                if (_binding == null) return@launch
                if (!response.success) {
                    Toast.makeText(requireContext(), "Не удалось удалить", Toast.LENGTH_SHORT).show()
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

    private fun onSaveOrder(list: List<ProfileVideo>) {
        if (list.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val orderIds = list.map { it.id }
                ApiClient.createJwtService(token)
                    .reorderPlaylist(playlistId, ru.asmr1.app.data.ReorderRequest(order = orderIds))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PlaylistFragment", "reorder error", e)
            }
        }
    }

    private fun updateCount(count: Int) {
        _binding?.playlistCount?.text = "$count видео"
    }

    private fun showLoading(show: Boolean) {
        _binding?.playlistLoading?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(show: Boolean) {
        _binding?.playlistEmpty?.visibility = if (show) View.VISIBLE else View.GONE
        _binding?.playlistRecycler?.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showError(msg: String) {
        if (_binding == null) return
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        showEmpty(true)
    }

    private fun openPlayerFromPlaylist(pv: ProfileVideo) {
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
            author = Author(id = pv.authorId, name = pv.authorName, avatar = ""),
            tags = null
        )
        (activity as? MainActivity)?.openPlayer(video)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        deleteJob?.cancel()
        _binding = null
    }
}
