package ru.asmr1.app.profile

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import ru.asmr1.app.R
import ru.asmr1.app.auth.AuthManager
import ru.asmr1.app.data.ApiClient
import ru.asmr1.app.data.UpdatePlaylistRequest
import ru.asmr1.app.databinding.SheetEditPlaylistBinding

class EditPlaylistSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ID = "playlist_id"
        private const val ARG_TITLE = "playlist_title"
        private const val ARG_VISIBILITY = "playlist_visibility"

        fun newInstance(id: Int, title: String, visibility: String) = EditPlaylistSheet().apply {
            arguments = Bundle().apply {
                putInt(ARG_ID, id)
                putString(ARG_TITLE, title)
                putString(ARG_VISIBILITY, visibility)
            }
        }
    }

    /** Колбэк после успешного сохранения */
    var onSaved: ((Int, String, String) -> Unit)? = null

    /** Колбэк после удаления */
    var onDeleted: ((Int) -> Unit)? = null

    private var _binding: SheetEditPlaylistBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private var playlistId = 0
    private var initialTitle = ""
    private var initialVisibility = "public"

    private var timer: CountDownTimer? = null
    private var isDeleting = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetEditPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        playlistId = requireArguments().getInt(ARG_ID, 0)
        initialTitle = requireArguments().getString(ARG_TITLE) ?: ""
        initialVisibility = requireArguments().getString(ARG_VISIBILITY) ?: "public"

        binding.etPlaylistTitle.setText(initialTitle)

        // Ручное переключение RadioButton (без RadioGroup)
        binding.rbPublic.isChecked = initialVisibility != "private"
        binding.rbPrivate.isChecked = initialVisibility == "private"

        binding.rbPublic.setOnClickListener {
            binding.rbPublic.isChecked = true
            binding.rbPrivate.isChecked = false
        }
        binding.rbPrivate.setOnClickListener {
            binding.rbPrivate.isChecked = true
            binding.rbPublic.isChecked = false
        }

        binding.btnSavePlaylist.setOnClickListener { save() }
        binding.btnDeletePlaylist.setOnClickListener { startDeleteCountdown() }
    }

    // ============================================================
    // СОХРАНИТЬ
    // ============================================================
    private fun save() {
        val title = binding.etPlaylistTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.etPlaylistTitle.error = "Введите название"
            return
        }
        val visibility = if (binding.rbPrivate.isChecked && !binding.rbPublic.isChecked) "private" else "public"

        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etPlaylistTitle.windowToken, 0)

        binding.btnSavePlaylist.isEnabled = false
        binding.btnSavePlaylist.text = "Сохранение..."
        binding.editProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run { dismiss(); return@launch }
                val response = ApiClient.createJwtService(token)
                    .updatePlaylist(playlistId, UpdatePlaylistRequest(title, visibility))

                if (_binding == null) return@launch

                if (response.success && response.playlist != null) {
                    Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show()
                    onSaved?.invoke(playlistId, response.playlist.title, response.playlist.visibility)
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                    resetSaveButton()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetSaveButton()
                }
            }
        }
    }

    private fun resetSaveButton() {
        _binding?.btnSavePlaylist?.isEnabled = true
        _binding?.btnSavePlaylist?.text = "Сохранить"
        _binding?.editProgress?.visibility = View.GONE
    }

    // ============================================================
    // УДАЛЕНИЕ — С ТАЙМЕРОМ 5 СЕК
    // ============================================================
    private fun startDeleteCountdown() {
        if (isDeleting) return
        isDeleting = true
        binding.btnDeletePlaylist.isEnabled = false

        timer?.cancel()
        timer = object : CountDownTimer(5000L, 1000L) {
            override fun onTick(ms: Long) {
                val seconds = (ms / 1000) + 1
                _binding?.btnDeletePlaylist?.text = "Удалить через $seconds…"
            }

            override fun onFinish() {
                _binding?.btnDeletePlaylist?.text = "Удалить плейлист"
                _binding?.btnDeletePlaylist?.isEnabled = true
                isDeleting = false
                showDeleteConfirmDialog()
            }
        }.start()
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить плейлист?")
            .setMessage("Все видео будут убраны из плейлиста. Это действие нельзя отменить.")
            .setPositiveButton("Да, удалить") { _, _ -> performDelete() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performDelete() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = authManager.getToken() ?: run { dismiss(); return@launch }
                val response = ApiClient.createJwtService(token).deletePlaylist(playlistId)
                if (_binding == null) return@launch

                if (response.success) {
                    Toast.makeText(requireContext(), "Плейлист удалён", Toast.LENGTH_SHORT).show()
                    onDeleted?.invoke(playlistId)
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), response.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        timer = null
        _binding = null
    }
}
