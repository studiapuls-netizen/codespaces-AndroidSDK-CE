package ru.asmr1.app.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.asmr1.app.MainActivity
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.LoginRequest
import ru.asmr1.app.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        binding.btnLogin.setOnClickListener { performLogin() }
        binding.btnGuest.setOnClickListener { closeLogin() }
        binding.btnClose.setOnClickListener { closeLogin() }
        binding.btnRegister.setOnClickListener {
            (activity as? MainActivity)?.showRegisterScreen()
        }
    }

    private fun performLogin() {
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        if (username.isEmpty() || password.isEmpty()) {
            toast("Заполните все поля")
            return
        }

        showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.authApi.login(
                    LoginRequest(username = username, password = password)
                )
                val token = response.token

                authManager.saveAuth(
                    token = token,
                    username = response.userNicename,
                    displayName = response.userDisplayName,
                    email = response.userEmail,
                    userId = 0
                )

                try {
                    val userApi = ApiClient.createJwtAuthService(token)
                    val user = userApi.getCurrentUser()
                    authManager.saveAuth(
                        token = token,
                        username = user.slug ?: response.userNicename,
                        displayName = user.name,
                        email = response.userEmail,
                        userId = user.id
                    )
                } catch (_: Exception) { }

                toast("Добро пожаловать, ${response.userDisplayName}!")
                (activity as? MainActivity)?.onLoginSuccess()

            } catch (e: Exception) {
                showLoading(false)
                val msg = e.message ?: "Неизвестная ошибка"
                val cleanMsg = when {
                    msg.contains("401") || msg.contains("403") -> "Неверный логин или пароль"
                    msg.contains("timeout", true) -> "Сервер не отвечает"
                    else -> "Ошибка: $msg"
                }
                toast(cleanMsg)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        _binding?.apply {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            btnLogin.isEnabled = !show
            btnRegister.isEnabled = !show
            btnGuest.isEnabled = !show
            etUsername.isEnabled = !show
            etPassword.isEnabled = !show
        }
    }

    private fun closeLogin() {
        parentFragmentManager.popBackStack()
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
