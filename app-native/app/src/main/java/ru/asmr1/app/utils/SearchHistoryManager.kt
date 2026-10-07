package ru.asmr1.app.utils

import android.content.Context

/**
 * Локальная история поиска — сохраняется в SharedPreferences.
 * Максимум 20 запросов, новые сверху, без дубликатов.
 */
class SearchHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences("asmr1_search_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "history_items"
        private const val SEPARATOR = "|||"
        private const val MAX_ITEMS = 20
    }

    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        val current = getHistory().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val limited = current.take(MAX_ITEMS)
        prefs.edit().putString(KEY_HISTORY, limited.joinToString(SEPARATOR)).apply()
    }

    fun removeQuery(query: String) {
        val current = getHistory().toMutableList()
        current.removeAll { it == query }
        prefs.edit().putString(KEY_HISTORY, current.joinToString(SEPARATOR)).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun isEmpty(): Boolean = getHistory().isEmpty()
}
