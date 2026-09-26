package ru.asmr1.app.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import ru.asmr1.app.R
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentPlayerBinding
import ru.asmr1.app.utils.formatDuration
import ru.asmr1.app.utils.formatViews
import ru.asmr1.app.utils.timeAgo

class PlayerFragment : Fragment() {

    companion object {
        const val ARG_VIDEO_ID = "video_id"
        const val ARG_VIDEO_URL = "video_url"
        const val ARG_TITLE = "title"
        const val ARG_AUTHOR_ID = "author_id"
        const val ARG_AUTHOR_NAME = "author_name"
        const val ARG_AUTHOR_AVATAR = "author_avatar"
        const val ARG_VIEWS = "views"
        const val ARG_PUBLISHED = "published"
        const val ARG_DESCRIPTION = "description"
        const val ARG_THUMBNAIL = "thumbnail"

        fun newInstance(video: Video): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_VIDEO_ID, video.id)
                    putString(ARG_VIDEO_URL, video.videoUrl)
                    putString(ARG_TITLE, video.title)
                    putInt(ARG_AUTHOR_ID, video.author.id)
                    putString(ARG_AUTHOR_NAME, video.author.name)
                    putString(ARG_AUTHOR_AVATAR, video.author.avatar)
                    putInt(ARG_VIEWS, video.views)
                    putString(ARG_PUBLISHED, video.published)
                    putString(ARG_DESCRIPTION, video.description)
                    putString(ARG_THUMBNAIL, video.thumbnail)
                }
            }
        }
    }

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val videoUrl = args.getString(ARG_VIDEO_URL) ?: ""
        val title = args.getString(ARG_TITLE) ?: ""
        val authorName = args.getString(ARG_AUTHOR_NAME) ?: ""
        val authorAvatar = args.getString(ARG_AUTHOR_AVATAR) ?: ""
        val views = args.getInt(ARG_VIEWS)
        val published = args.getString(ARG_PUBLISHED) ?: ""
        val description = args.getString(ARG_DESCRIPTION) ?: ""
        val thumbnail = args.getString(ARG_THUMBNAIL) ?: ""

        // Заполняем UI
        binding.title.text = title
        binding.authorName.text = authorName
        binding.meta.text = "${formatViews(views)} • ${timeAgo(published)}"
        binding.description.text = description

        if (authorAvatar.isNotEmpty()) {
            binding.authorAvatar.load(authorAvatar)
        }

        if (thumbnail.isNotEmpty() && videoUrl.isEmpty()) {
            binding.thumbnail.load(thumbnail)
        }

        // Кнопка закрытия
        binding.btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // ExoPlayer
        player = ExoPlayer.Builder(requireContext()).build().also { exo ->
            binding.playerView.player = exo

            if (videoUrl.isNotEmpty()) {
                val mediaItem = MediaItem.fromUri(videoUrl)
                exo.setMediaItem(mediaItem)
                exo.prepare()
                exo.playWhenReady = true
            }

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        parentFragmentManager.popBackStack()
                    }
                }
            })
        }
    }

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onStop() {
        super.onStop()
        // Паузим если не в PiP
        val activity = requireActivity()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N ||
            !activity.isInPictureInPictureMode) {
            player?.pause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        _binding = null
    }

    fun getPlayer(): ExoPlayer? = player
}
