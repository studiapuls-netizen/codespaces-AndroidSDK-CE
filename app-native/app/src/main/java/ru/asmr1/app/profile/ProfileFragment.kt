package ru.asmr1.app.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import coil.load
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager

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

        loadUserData()

        binding.rowMyVideos.setOnClickListener {
            Toast.makeText(context, "Мои видео — скоро", Toast.LENGTH_SHORT).show()
        }
        binding.rowHistory.setOnClickListener {
            Toast.makeText(context, "История — скоро", Toast.LENGTH_SHORT).show()
        }
        binding.rowPlaylists.setOnClickListener {
            Toast.makeText(context, "Плейлисты — скоро", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadUserData() {
        // Имя и email из AuthManager
        val name = authManager.getDisplayName() ?: "Пользователь"
        val email = authManager.getEmail() ?: ""

        binding.tvDisplayName.text = name
        binding.tvEmail.text = email

        // Аватар — Gravatar по email
        if (email.isNotEmpty()) {
            val hash = md5(email.trim().lowercase())
            val avatarUrl = "https://www.gravatar.com/avatar/$hash?s=200&d=mp"
            binding.ivAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(R.color.card_bg)
                error(R.color.card_bg)
            }
        }
    }

    private fun logout() {
        // Вся логика логаута — в MainActivity (единая точка)
        (activity as? MainActivity)?.logout()
    }

    /** MD5-хеш для Gravatar (стандарт) */
    private fun md5(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
