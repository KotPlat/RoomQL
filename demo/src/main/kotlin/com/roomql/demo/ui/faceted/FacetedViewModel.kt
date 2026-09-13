package com.roomql.demo.ui.faceted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomql.demo.data.CatalogueRepository
import com.roomql.demo.data.CatalogueSeed
import com.roomql.demo.data.DatabaseProvider
import com.roomql.demo.data.ProductWithBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FacetedUiState(
    val selectedCategories: Set<String> = emptySet(),
    val country: String? = null,
    val results: List<ProductWithBrand> = emptyList(),
    val sql: String = "",
) {
    val categories: List<String> get() = CatalogueSeed.categories
    val countries: List<String> get() = listOf("Japan", "Germany", "Sweden")
}

class FacetedViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)
    private val repository = CatalogueRepository(db.productDao())

    private val _state = MutableStateFlow(FacetedUiState())
    val state: StateFlow<FacetedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DatabaseProvider.seedIfEmpty(db) }
            run()
        }
    }

    fun toggleCategory(category: String) {
        val current = _state.value.selectedCategories
        _state.value = _state.value.copy(
            selectedCategories = if (category in current) current - category else current + category,
        )
        viewModelScope.launch { run() }
    }

    /** One tap back to "no categories selected", which is also "no filter". */
    fun clearCategories() {
        _state.value = _state.value.copy(selectedCategories = emptySet())
        viewModelScope.launch { run() }
    }

    fun onCountry(country: String?) {
        _state.value = _state.value.copy(country = country)
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        val current = _state.value
        val result = withContext(Dispatchers.IO) {
            // An empty set is passed straight through: RoomQL drops the IN clause, so
            // "nothing selected" correctly means "no filter".
            repository.facets(current.selectedCategories.toList(), current.country)
        }
        _state.value = _state.value.copy(results = result.rows, sql = result.sql)
    }
}
