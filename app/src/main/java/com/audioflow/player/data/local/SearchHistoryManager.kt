package com.audioflow.player.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages search history using SharedPreferences
 */
@Singleton
class SearchHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "search_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_SIZE = 20
        private const val SEPARATOR = "|||"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _history = MutableStateFlow<List<String>>(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()
    
    private fun loadHistory(): List<String> {
        val historyString = prefs.getString(KEY_HISTORY, "") ?: ""
        return if (historyString.isEmpty()) {
            emptyList()
        } else {
            historyString.split(SEPARATOR).filter { it.isNotBlank() }
        }
    }
    
    private fun saveHistory(history: List<String>) {
        prefs.edit().putString(KEY_HISTORY, history.joinToString(SEPARATOR)).apply()
        _history.value = history
    }
    
    /**
     * Add a search query to history (moves to top if exists)
     */
    fun addSearch(query: String) {
        if (query.isBlank()) return
        
        val current = _history.value.toMutableList()
        // Remove if already exists (to move to top)
        current.remove(query)
        // Add to beginning
        current.add(0, query)
        // Limit size
        val trimmed = current.take(MAX_HISTORY_SIZE)
        saveHistory(trimmed)
    }
    
    /**
     * Remove a single item from history
     */
    fun removeItem(query: String) {
        val current = _history.value.toMutableList()
        current.remove(query)
        saveHistory(current)
    }
    
    /**
     * Clear all search history
     */
    fun clearAll() {
        saveHistory(emptyList())
    }
}
