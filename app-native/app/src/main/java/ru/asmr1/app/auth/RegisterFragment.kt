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
import ru.asmr1.app.data.RegisterRequest
import ru.asmr1.app.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager

    private var captchaId: String = ""
    private var captchaLoaded: Boolean = false
    private var isSubmitting: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        binding.btnClose.setOnClickListener { closeRegister() }
        binding.btnBackToLogin.setOnClickListener { closeRegister() }
        binding.btnRegister.setOnClickListener { performRegister() }
        binding.btnRefreshCaptcha.setOnClickListener { loadCaptcha() }

        // Сразу загружаем капчу
        loadCaptcha()
    }

    // ============================================================
    // ЗАГРУЗКА КАПЧИ
    // ============================================================
    private fun loadCaptcha() {
        if (_binding == null) return

        captchaLoaded = false
        binding.tvCaptchaQuestion.text = "Загрузка..."
        binding.etCaptchaAnswer.setText("")
        binding.btnRefreshCaptcha.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.authApi.getRegisterCaptcha()
                if (_binding == null) return@launch

                if (response.success) {
                    captchaId = response.captchaId
                    binding.tvCaptchaQuestion.text = "${response.question} ="
                    captchaLoaded = true
                } else {
                    binding.tvCaptchaQuestion.text = "Ошибка капчи"
                    toast("Не удалось загрузить капчу: ${response.message ?: "?"}")
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.tvCaptchaQuestion.text = "Ошибка сети"
                toast("Не удалось загрузить капчу. Проверьте интернет")
            } finally {
                _binding?.btnRefreshCaptcha?.isEnabled = true
            }
        }
    }

    // ============================================================
    // РЕГИСТРАЦИЯ
    // ============================================================
    private fun performRegister() {
        if (isSubmitting) return
        if (_binding == null) return

        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val captchaAnswer = binding.etCaptchaAnswer.text?.toString()?.trim().orEmpty()

        // ===== ВАЛИДАЦИЯ НА КЛИЕНТЕ =====
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || captchaAnswer.isEmpty()) {
            toast("Заполните все поля")
            return
        }
        if (username.length < 3) {
            toast("Логин минимум 3 символа")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Некорректный email")
            return
        }
        if (password.length < 6) {
            toast("Пароль минимум 6 символов")
            return
        }
        if (!captchaLoaded || captchaId.isEmpty()) {
            toast("Капча не загружена. Подождите...")
            loadCaptcha()
            return
        }

        showLoading(true)
        isSubmitting = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.authApi.register(
                    RegisterRequest(
                        username = username,
                        email = email,
                        password = password,
                        captchaId = captchaId,
                        captchaAnswer = captchaAnswer
                    )
                )

                if (_binding == null) return@launch

                if (response.success && !response.token.isNullOrEmpty()) {
                    // Сохраняем токен
                    authManager.saveAuth(
                        token = response.token,
                        username = response.userNicename ?: username,
                        displayName = response.userDisplayName ?: username,
                        email = response.userEmail ?: email,
                        userId = response.userId ?: 0
                    )

                    // Обновляем профиль (как в LoginFragment)
                    try {
                        val userApi = ApiClient.createJwtAuthService(response.token)
                        val user = userApi.getCurrentUser()
                        authManager.saveAuth(
                            token = response.token,
                            username = user.slug ?: (response.userNicename ?: username),
                            displayName = user.name,
                            email = response.userEmail ?: email,
                            userId = user.id
                        )
                    } catch (_: Exception) { }

                    toast("Добро пожаловать, ${response.userDisplayName ?: username}!")
                    (activity as? MainActivity)?.onLoginSuccess()

                } else {
                    // Ошибка от сервера
                    showLoading(false)
                    isSubmitting = false

                    val errMsg = response.message ?: "Не удалось зарегистрироваться"
                    toast(errMsg)

                    // Если проблема с капчей — грузим новую
                    val errCode = response.code ?: ""
                    if (errCode.startsWith("captcha")) {
                        loadCaptcha()
                    }
                }

            } catch (e: Exception) {
                if (_binding == null) return@launch
                showLoading(false)
                isSubmitting = false

                val msg = e.message ?: "Неизвестная ошибка"
                val cleanMsg = when {
                    msg.contains("409") -> "Логин или email уже заняты"
                    msg.contains("timeout", true) -> "Сервер не отвечает"
                    msg.contains("429") -> "Слишком много попыток. Подождите"
                    else -> "Ошибка: $msg"
                }
                toast(cleanMsg)
                loadCaptcha()
            }
        }
    }

    // ============================================================
    // UI-ХЕЛПЕРЫ
    // ============================================================
    private fun showLoading(show: Boolean) {
        _binding?.apply {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            btnRegister.isEnabled = !show
            btnBackToLogin.isEnabled = !show
            etUsername.isEnabled = !show
            etEmail.isEnabled = !show
            etPassword.isEnabled = !show
            etCaptchaAnswer.isEnabled = !show
            btnRefreshCaptcha.isEnabled = !show
        }
    }

    private fun closeRegister() {
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
