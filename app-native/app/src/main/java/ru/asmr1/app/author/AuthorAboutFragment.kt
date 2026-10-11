package ru.asmr1.app.author

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ru.asmr1.app.data.AuthorPageResponse
import ru.asmr1.app.databinding.FragmentAuthorAboutBinding
import ru.asmr1.app.utils.timeAgo

class AuthorAboutFragment : Fragment() {

    companion object {
        private const val ARG_DATA = "data"
        fun newInstance(data: AuthorPageResponse) = AuthorAboutFragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_DATA, data) }
        }
    }

    private var _binding: FragmentAuthorAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthorAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val data = arguments?.getParcelable<AuthorPageResponse>(ARG_DATA) ?: return

        binding.tvNickname.text = data.nickname
        binding.tvDescription.text = data.description ?: "Описание не заполнено"
        binding.tvSubscribers.text = data.subscribersCount.toString()
        binding.tvVideos.text = data.videoCount.toString()
        binding.tvViews.text = data.totalViews.toString()

        if (!data.channelJoined.isNullOrEmpty()) {
            binding.tvJoined.text = "На сайте с ${data.channelJoined.take(10)}"
        } else {
            binding.tvJoined.visibility = View.GONE
        }

        // Соцсети
        val s = data.socials
        binding.socialsContainer.removeAllViews()
        if (s != null) {
            val map = mapOf(
                "YouTube" to s.youtube,
                "Telegram" to s.telegram,
                "VK" to s.vk,
                "Rutube" to s.rutube,
                "Дзен" to s.dzen,
                "Boosty" to s.boosty,
                "Patreon" to s.patreon,
                "DonationAlerts" to s.donationalerts,
                "Сайт" to s.website
            )
            for ((name, url) in map) {
                if (!url.isNullOrEmpty()) {
                    val tv = android.widget.TextView(requireContext())
                    tv.text = "$name: $url"
                    tv.setTextColor(0xFFE6E6E6.toInt())
                    tv.textSize = 14f
                    tv.setPadding(0, 8, 0, 8)
                    binding.socialsContainer.addView(tv)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
