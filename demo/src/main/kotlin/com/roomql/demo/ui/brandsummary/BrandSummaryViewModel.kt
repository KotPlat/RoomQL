package com.roomql.demo.ui.brandsummary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomql.demo.data.BrandSummary
import com.roomql.demo.data.CatalogueRepository
import com.roomql.demo.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrandSummaryUiState(
    val inStockOnly: Boolean = false,
    val rows: List<BrandSummary> = emptyList(),
    val sql: String = "",
)

class BrandSummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)
    private val repository = CatalogueRepository(db.productDao())

    private val _state = MutableStateFlow(BrandSummaryUiState())
    val state: StateFlow<BrandSummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DatabaseProvider.seedIfEmpty(db) }
            refresh()
        }
    }

    fun toggleInStockOnly() {
        _state.value = _state.value.copy(inStockOnly = !_state.value.inStockOnly)
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val inStockOnly = _state.value.inStockOnly
            val result = withContext(Dispatchers.IO) { repository.brandSummary(inStockOnly) }
            _state.value = _state.value.copy(rows = result.rows, sql = result.sql)
        }
    }
}
