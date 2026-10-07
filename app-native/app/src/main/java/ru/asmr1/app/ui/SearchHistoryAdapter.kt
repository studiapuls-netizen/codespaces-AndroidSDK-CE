package ru.asmr1.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.asmr1.app.databinding.ItemSearchHistoryBinding

class SearchHistoryAdapter(
    private var items: List<String> = emptyList(),
    private val onItemClick: (String) -> Unit,
    private val onItemDelete: (String) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.HistoryViewHolder>() {

    fun setItems(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemSearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemSearchHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(query: String) {
            binding.tvQuery.text = query
            binding.root.setOnClickListener { onItemClick(query) }
            binding.btnDelete.setOnClickListener { onItemDelete(query) }
        }
    }
}
