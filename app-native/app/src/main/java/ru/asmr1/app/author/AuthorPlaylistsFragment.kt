package ru.asmr1.app.author

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.asmr1.app.data.AuthorPageResponse
import ru.asmr1.app.databinding.FragmentAuthorPlaylistsBinding
import ru.asmr1.app.profile.PlaylistBigAdapter
import ru.asmr1.app.profile.PlaylistCard

class AuthorPlaylistsFragment : Fragment() {

    companion object {
        private const val ARG_DATA = "data"
        fun newInstance(data: AuthorPageResponse) = AuthorPlaylistsFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_DATA, data) }
        }
    }

    private var _binding: FragmentAuthorPlaylistsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PlaylistBigAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthorPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val data = arguments?.getParcelable<AuthorPageResponse>(ARG_DATA) ?: return

        val playlists = data.tabs?.playlists?.map { p ->
            PlaylistCard(
                id = p.id,
                title = p.title,
                count = p.count,
                type = p.type,
                firstVideo = null,
                visibility = p.visibility
            )
        } ?: emptyList()

        adapter = PlaylistBigAdapter(items = playlists, onClick = { /* TODO */ })

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
