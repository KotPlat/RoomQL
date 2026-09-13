package com.roomql.demo.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomql.demo.data.CatalogueSeed
import com.roomql.demo.data.DatabaseProvider
import com.roomql.demo.data.ProductEntity
import com.roomql.demo.search.ConcatSearch
import com.roomql.demo.search.IsNullOrSearch
import com.roomql.demo.search.OverloadedSearch
import com.roomql.demo.search.RoomQlSearch
import com.roomql.demo.search.SearchFilters
import com.roomql.demo.search.SearchStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val strategyIndex: Int = 0,
    val products: List<ProductEntity> = emptyList(),
    val sql: String = "",
    val loading: Boolean = true,
) {
    val categories: List<String> get() = CatalogueSeed.categories
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)

    private val strategies: List<SearchStrategy> = listOf(
        RoomQlSearch(db.productDao()),
        IsNullOrSearch(db.isNullOrProductDao()),
        OverloadedSearch(db.overloadedProductDao()),
        ConcatSearch(db.productDao()),
    )

    val strategyLabels: List<String> = strategies.map { it.label }

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** The in-flight query, cancelled whenever a newer one starts so the latest input wins. */
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DatabaseProvider.seedIfEmpty(db) }
            refresh()
        }
    }

    /** The selected strategy, so the screen can show its cost and call-site code. */
    fun currentStrategy(): SearchStrategy = strategies[_state.value.strategyIndex]

    fun onCategory(category: String?) = update { it.copy(category = category) }
    fun onMinPrice(minPrice: Double?) = update { it.copy(minPrice = minPrice) }
    fun onMinRating(minRating: Double?) = update { it.copy(minRating = minRating) }
    fun onInStockOnly(inStockOnly: Boolean?) = update { it.copy(inStockOnly = inStockOnly) }

    /** Back to "no filters at all" in one tap — the state users otherwise struggle to reach. */
    fun clearFilters() {
        _state.value = _state.value.copy(filters = SearchFilters())
        refresh()
    }

    fun onStrategy(index: Int) {
        _state.value = _state.value.copy(strategyIndex = index)
        refresh()
    }

    private fun update(transform: (SearchFilters) -> SearchFilters) {
        _state.value = _state.value.copy(filters = transform(_state.value.filters))
        refresh()
    }

    /**
     * Re-runs the query, cancelling any query still in flight.
     *
     * Filter taps arrive faster than SQLite answers, so without the cancel two runs can
     * overlap and the slower one wins, painting results for filters the user has already
     * moved past.
     */
    private fun refresh() {
        queryJob?.cancel()
        queryJob = viewModelScope.launch { run() }
    }

    /**
     * Runs the currently selected strategy. Switching strategy must change the SQL on
     * screen but never the rows — that invariant is asserted by FlagshipSearchTest.
     */
    private suspend fun run() {
        val requested = _state.value
        val result = withContext(Dispatchers.IO) {
            strategies[requested.strategyIndex].search(requested.filters)
        }
        // Merge into whatever the state is *now*, not into the snapshot this run started
        // from: the user may have changed a filter while the query was running, and
        // copying the stale snapshot back would silently undo their input.
        _state.value = _state.value.copy(
            products = result.products,
            sql = result.sql,
            loading = false,
        )
    }
}
