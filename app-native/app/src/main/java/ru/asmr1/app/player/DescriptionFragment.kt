package ru.asmr1.app.player

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.asmr1.app.MainActivity
import ru.asmr1.app.data.Video
import ru.asmr1.app.databinding.FragmentDescriptionBinding
import ru.asmr1.app.utils.formatViews
import ru.asmr1.app.utils.timeAgo

class DescriptionFragment : Fragment() {

    companion object {
        const val ARG_VIDEO = "video"
        fun newInstance(video: Video) = DescriptionFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_VIDEO, video) }
        }
    }

    private var _binding: FragmentDescriptionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDescriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val video = requireArguments().getParcelable<Video>(ARG_VIDEO) ?: return

        binding.descTitle.text = video.title
        binding.descMeta.text = "${formatViews(video.views)} • ${timeAgo(video.published)}"
        binding.descText.text = video.description

        // Кликабельные ссылки (http, https, www)
        Linkify.addLinks(binding.descText, Linkify.WEB_URLS)
        binding.descText.movementMethod = LinkMovementMethod.getInstance()

        binding.btnDescClose.setOnClickListener {
            (requireActivity() as MainActivity).closeDescription()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
