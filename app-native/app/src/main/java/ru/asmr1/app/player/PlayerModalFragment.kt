package ru.asmr1.app.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentPlayerModalBinding

class PlayerModalFragment : Fragment() {

    companion object {
        const val ARG_VIDEO = "video"
        fun newInstance(video: Video) = PlayerModalFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_VIDEO, video) }
        }
    }

    private var _binding: FragmentPlayerModalBinding? = null
    private val binding get() = _binding!!
    private var player: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerModalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val video = requireArguments().getParcelable<Video>(ARG_VIDEO) ?: return

        player = ExoPlayer.Builder(requireContext()).build().also { exo ->
            binding.playerView.player = exo
            if (video.videoUrl.isNotEmpty()) {
                exo.setMediaItem(MediaItem.fromUri(video.videoUrl))
                exo.prepare()
                exo.playWhenReady = true
            }
        }

        binding.btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        _binding = null
    }
}
