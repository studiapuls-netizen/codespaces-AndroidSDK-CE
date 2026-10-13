package ru.asmr1.app.author

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.AuthorPageResponse
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentAuthorVideosBinding
import ru.asmr1.app.player.AddToPlaylistSheet
import ru.asmr1.app.ui.VideoAdapter

class AuthorHomeFragment : Fragment() {

    companion object {
        private const val ARG_DATA = "data"
        fun newInstance(data: AuthorPageResponse) = AuthorHomeFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_DATA, data) }
        }
    }

    private var _binding: FragmentAuthorVideosBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VideoAdapter
    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthorVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        authManager = AuthManager(requireContext())

        val data = arguments?.getParcelable<AuthorPageResponse>(ARG_DATA) ?: return

        adapter = VideoAdapter(
            videos = data.tabs?.popular ?: emptyList(),
            onClick = { video ->
                (requireActivity() as? ru.asmr1.app.MainActivity)?.openPlayer(video)
            },
            onMoreClick = { video, _ -> showVideoMenu(video) },
            hideAuthor = true
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val header = parentFragment as? AuthorPageFragment ?: return
                header.scrollAppBar(dy)
            }
        })
        binding.swipeRefresh.isEnabled = false
    }

    // ============================================================
    // МЕНЮ ПОД КАРТОЧКОЙ (⋯)
    // ============================================================

    private fun showVideoMenu(video: Video) {
        val sheetView = layoutInflater.inflate(R.layout.sheet_author_video_menu, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        dialog.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.menu_watch_later)?.setOnClickListener {
            dialog.dismiss()
            addToWatchLater(video)
        }

        sheetView.findViewById<View>(R.id.menu_save_playlist)?.setOnClickListener {
            dialog.dismiss()
            if (!authManager.isLoggedIn()) {
                Toast.makeText(requireContext(), "Войдите, чтобы сохранять", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sheet = AddToPlaylistSheet.newInstance(video.id)
            sheet.show(childFragmentManager, "add_to_playlist")
        }

        sheetView.findViewById<View>(R.id.menu_share)?.setOnClickListener {
            dialog.dismiss()
            shareVideo(video)
        }

        sheetView.findViewById<View>(R.id.menu_download)?.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "Скачивание — скоро", Toast.LENGTH_SHORT).show()
        }

        sheetView.findViewById<View>(R.id.menu_cancel)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addToWatchLater(video: Video) {
        if (!authManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Войдите, чтобы сохранять", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: return@launch
                val api = ApiClient.createJwtService(token)
                val profile = api.getMeProfile()
                val watchLaterId = profile.watchlater.playlistId
                if (watchLaterId <= 0) {
                    Toast.makeText(requireContext(), "Ошибка плейлиста", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val response = api.addToPlaylist(watchLaterId, video.id)
                if (response.success) {
                    val msg = if (response.alreadyIn) "Уже в «Смотреть позже»" else "Добавлено в «Смотреть позже»"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareVideo(video: Video) {
        try {
            val url = "https://asmr1.ru/watch/${video.id}/"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url + "\n\n" + video.title)
                putExtra(Intent.EXTRA_SUBJECT, video.title)
            }
            startActivity(Intent.createChooser(intent, "Поделиться"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
