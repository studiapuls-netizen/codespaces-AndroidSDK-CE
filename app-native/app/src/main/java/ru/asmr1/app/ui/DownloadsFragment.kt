package ru.asmr1.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.asmr1.app.MainActivity
import ru.asmr1.app.R
import ru.asmr1.app.databinding.FragmentDownloadsBinding
import ru.asmr1.app.download.DownloadStorage
import ru.asmr1.app.download.DownloadedFile
import ru.asmr1.app.download.Downloader

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DownloadsAdapter
    private lateinit var storage: DownloadStorage
    private lateinit var downloader: Downloader

    // Фильтры
    private var typeFilter: DownloadedFile.Type? = null  // null = все
    private var sortDesc: Boolean = true                 // true = ↓ новые

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        storage = DownloadStorage(requireContext())
        downloader = Downloader(requireContext())

        setupRecycler()
        setupChips()
        loadDownloads()
    }

    private fun setupRecycler() {
        adapter = DownloadsAdapter(
            onClick = { item ->
                // Открываем плеер — он сам определит локальный/онлайн
                (requireActivity() as? MainActivity)?.openPlayer(item.toVideo())
            },
            onDelete = { item ->
                confirmDelete(item)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.itemAnimator = null
    }

    private fun setupChips() {
        binding.chipAll.setOnClickListener {
            typeFilter = null
            updateChipsUI()
            loadDownloads()
        }
        binding.chipVideo.setOnClickListener {
            typeFilter = DownloadedFile.Type.VIDEO
            updateChipsUI()
            loadDownloads()
        }
        binding.chipAudio.setOnClickListener {
            typeFilter = DownloadedFile.Type.AUDIO
            updateChipsUI()
            loadDownloads()
        }
        binding.chipOrder.setOnClickListener {
            sortDesc = !sortDesc
            updateChipsUI()
            loadDownloads()
        }

        updateChipsUI()
    }

    private fun updateChipsUI() {
        _binding?.apply {
            setChipActive(chipAll, typeFilter == null)
            setChipActive(chipVideo, typeFilter == DownloadedFile.Type.VIDEO)
            setChipActive(chipAudio, typeFilter == DownloadedFile.Type.AUDIO)
            chipOrder.text = if (sortDesc) "↓ Новые" else "↑ Старые"
            setChipActive(chipOrder, false)
        }
    }

    private fun setChipActive(chip: TextView, active: Boolean) {
        chip.isSelected = active
        chip.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) android.R.color.white else R.color.text_secondary
            )
        )
    }

    private fun loadDownloads() {
        val list = storage.getDownloads(typeFilter).let {
            if (sortDesc) it.sortedByDescending { d -> d.downloadedAt }
            else it.sortedBy { d -> d.downloadedAt }
        }

        if (list.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.llEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.llEmpty.visibility = View.GONE
        }

        adapter.setItems(list)
    }

    private fun confirmDelete(item: DownloadedFile) {
        val typeName = if (item.type == DownloadedFile.Type.VIDEO) "видео" else "аудио"
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Удалить?")
            .setMessage("Удалить скачанное $typeName «${item.title}»?")
            .setPositiveButton("Удалить") { _, _ ->
                downloader.delete(item.videoId, item.type)
                Toast.makeText(requireContext(), "Удалено", Toast.LENGTH_SHORT).show()
                loadDownloads()
                // Обновляем фрагмент — таб «Скачано» может скрыться, если ничего не осталось
                (activity as? MainActivity)?.supportFragmentManager
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // При возврате — обновляем список (вдруг что-то удалили снаружи)
        if (_binding != null) loadDownloads()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
