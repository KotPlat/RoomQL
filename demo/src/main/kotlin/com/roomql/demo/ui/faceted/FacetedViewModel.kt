package com.roomql.demo.ui.faceted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomql.demo.data.CatalogueRepository
import com.roomql.demo.data.CatalogueSeed
import com.roomql.demo.data.DatabaseProvider
import com.roomql.demo.data.ProductWithBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    /** Every country the seed actually has a brand in, so no brand is unreachable. */
    val countries: List<String> get() = CatalogueSeed.countries
}

class FacetedViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)
    private val repository = CatalogueRepository(db.productDao())

    private val _state = MutableStateFlow(FacetedUiState())
    val state: StateFlow<FacetedUiState> = _state.asStateFlow()

    /** The in-flight query, cancelled whenever a newer one starts so the latest input wins. */
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DatabaseProvider.seedIfEmpty(db) }
            refresh()
        }
    }

    fun toggleCategory(category: String) {
        val current = _state.value.selectedCategories
        _state.value = _state.value.copy(
            selectedCategories = if (category in current) current - category else current + category,
        )
        refresh()
    }

    /** One tap back to "no categories selected", which is also "no filter". */
    fun clearCategories() {
        _state.value = _state.value.copy(selectedCategories = emptySet())
        refresh()
    }

    fun onCountry(country: String?) {
        _state.value = _state.value.copy(country = country)
        refresh()
    }

    /**
     * Re-runs the query, cancelling any query still in flight.
     *
     * Chip taps arrive faster than a joined query answers, so without the cancel two runs
     * can overlap and the slower one wins, showing results for a chip selection the user
     * has already changed.
     */
    private fun refresh() {
        queryJob?.cancel()
        queryJob = viewModelScope.launch { run() }
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
