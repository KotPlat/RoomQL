package com.roomql.demo.ui.sorted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomql.runtime.SortDirection
import com.roomql.demo.data.CatalogueRepository
import com.roomql.demo.data.DatabaseProvider
import com.roomql.demo.data.ProductEntity
import com.roomql.demo.data.SortColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SortedUiState(
    val sortColumn: SortColumn = SortColumn.Price,
    val direction: SortDirection = SortDirection.ASC,
    val page: Int = 0,
    val products: List<ProductEntity> = emptyList(),
    val sql: String = "",
) {
    val pageSize: Int get() = PAGE_SIZE

    companion object {
        const val PAGE_SIZE = 20
    }
}

class SortedViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)
    private val repository = CatalogueRepository(db.productDao())

    private val _state = MutableStateFlow(SortedUiState())
    val state: StateFlow<SortedUiState> = _state.asStateFlow()

    /** The in-flight query, cancelled whenever a newer one starts so the latest input wins. */
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DatabaseProvider.seedIfEmpty(db) }
            refresh()
        }
    }

    fun onSortColumn(column: SortColumn) {
        _state.value = _state.value.copy(sortColumn = column, page = 0)
        refresh()
    }

    fun toggleDirection() {
        val next =
            if (_state.value.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
        _state.value = _state.value.copy(direction = next, page = 0)
        refresh()
    }

    fun nextPage() {
        _state.value = _state.value.copy(page = _state.value.page + 1)
        refresh()
    }

    fun previousPage() {
        if (_state.value.page == 0) return
        _state.value = _state.value.copy(page = _state.value.page - 1)
        refresh()
    }

    /**
     * Re-runs the query, cancelling any query still in flight.
     *
     * Paging taps arrive faster than SQLite answers, so without the cancel two runs can
     * overlap and the slower one wins, leaving the list showing a page the user has
     * already moved past.
     */
    private fun refresh() {
        queryJob?.cancel()
        queryJob = viewModelScope.launch { run() }
    }

    /**
     * Both the sort column and its direction are chosen at runtime. This is the case a
     * static @Query cannot express at all: a column name is not a bindable parameter.
     */
    private suspend fun run() {
        val current = _state.value
        val result = withContext(Dispatchers.IO) {
            repository.page(
                sortColumn = current.sortColumn,
                direction = current.direction,
                limit = SortedUiState.PAGE_SIZE,
                offset = current.page * SortedUiState.PAGE_SIZE,
            )
        }
        _state.value = _state.value.copy(products = result.rows, sql = result.sql)
    }
}
