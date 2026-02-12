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
 * Manages user's language and genre filter preferences.
 * Persisted via SharedPreferences. Filters are applied to
 * YouTube search queries and Home content suggestions.
 */
@Singleton
class FilterPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "audioflow_filters"
        private const val KEY_LANGUAGES = "selected_languages"
        private const val KEY_GENRES = "selected_genres"
        
        val AVAILABLE_LANGUAGES = listOf(
            "English", "Hindi", "Telugu", "Tamil", "Kannada",
            "Malayalam", "Korean", "Japanese", "Spanish", "Punjabi"
        )
        
        val AVAILABLE_GENRES = listOf(
            "Pop", "Hip-Hop", "Rock", "R&B", "EDM",
            "Bollywood", "K-Pop", "J-Pop", "Classical", "Jazz",
            "Lo-Fi", "Indie", "Country", "Latin", "Devotional"
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _selectedLanguages = MutableStateFlow(loadLanguages())
    val selectedLanguages: StateFlow<Set<String>> = _selectedLanguages.asStateFlow()
    
    private val _selectedGenres = MutableStateFlow(loadGenres())
    val selectedGenres: StateFlow<Set<String>> = _selectedGenres.asStateFlow()
    
    private fun loadLanguages(): Set<String> {
        return prefs.getStringSet(KEY_LANGUAGES, emptySet()) ?: emptySet()
    }
    
    private fun loadGenres(): Set<String> {
        return prefs.getStringSet(KEY_GENRES, emptySet()) ?: emptySet()
    }
    
    fun toggleLanguage(language: String) {
        val current = _selectedLanguages.value.toMutableSet()
        if (current.contains(language)) current.remove(language) else current.add(language)
        _selectedLanguages.value = current
        prefs.edit().putStringSet(KEY_LANGUAGES, current).apply()
    }
    
    fun toggleGenre(genre: String) {
        val current = _selectedGenres.value.toMutableSet()
        if (current.contains(genre)) current.remove(genre) else current.add(genre)
        _selectedGenres.value = current
        prefs.edit().putStringSet(KEY_GENRES, current).apply()
    }
    
    fun clearAll() {
        _selectedLanguages.value = emptySet()
        _selectedGenres.value = emptySet()
        prefs.edit().remove(KEY_LANGUAGES).remove(KEY_GENRES).apply()
    }
    
    /**
     * Returns a search query suffix based on active filters.
     * E.g., if "Hindi" and "Bollywood" are selected, returns "Hindi Bollywood".
     * This gets appended to search queries for more relevant results.
     */
    fun getFilterSuffix(): String {
        val parts = mutableListOf<String>()
        val langs = _selectedLanguages.value
        val genres = _selectedGenres.value
        if (langs.isNotEmpty()) parts.add(langs.first()) // Use primary language
        if (genres.isNotEmpty()) parts.add(genres.first()) // Use primary genre
        return parts.joinToString(" ")
    }
    
    fun hasActiveFilters(): Boolean {
        return _selectedLanguages.value.isNotEmpty() || _selectedGenres.value.isNotEmpty()
    }
}
