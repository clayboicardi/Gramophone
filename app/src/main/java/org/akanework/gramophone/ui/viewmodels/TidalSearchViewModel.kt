package org.akanework.gramophone.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.tidal.TidalApiClient
import org.akanework.gramophone.logic.tidal.TidalSearchResult

@OptIn(FlowPreview::class)
class TidalSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = TidalApiClient()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<TidalSearchResult?>(null)
    val searchResults: StateFlow<TidalSearchResult?> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { query -> performSearch(query) }
        }
    }

    fun setQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = null
            _error.value = null
        }
    }

    private suspend fun performSearch(query: String) {
        _isLoading.value = true
        _error.value = null
        try {
            val results = apiClient.search(query)
            _searchResults.value = results
        } catch (e: Exception) {
            _error.value = e.message ?: "Search failed"
            _searchResults.value = null
        } finally {
            _isLoading.value = false
        }
    }
}
