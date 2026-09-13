package com.roomql.demo.ui.typeahead

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomql.demo.data.CatalogueRepository
import com.roomql.demo.data.DatabaseProvider
import com.roomql.demo.data.ProductEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TypeAheadViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)
    private val repository = CatalogueRepository(db.productDao())

    private val _queryText = MutableStateFlow("")
    val queryText: StateFlow<String> = _queryText.asStateFlow()

    init {
        viewModelScope.launch { withContext(Dispatchers.IO) { DatabaseProvider.seedIfEmpty(db) } }
    }

    /**
     * Debounced type-ahead over a Flow query.
     *
     * The Flow keeps re-emitting because ProductDao.observe declares
     * `@RawQuery(observedEntities = [ProductEntity::class])`. Room cannot work out which
     * tables a raw query reads, so omitting that annotation argument produces a Flow that
     * emits once and then goes silent forever — the single most common RoomQL mistake,
     * and not a RoomQL bug.
     *
     * An empty input is passed through as null, so `contains` drops out of the SQL and
     * every row is returned rather than none.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val observed = _queryText
        .debounce(250)
        .map { text -> repository.observeByName(text.ifBlank { null }) }
        // Shared because both `results` and `sql` below consume this. Without it each
        // collector would run its own debounce and build its own query, so the rows on
        // screen and the SQL shown beneath them could describe different inputs.
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val results: StateFlow<List<ProductEntity>> = observed
        .flatMapLatest { it.flow }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The statement behind the current input, shown in the screen's SQL panel. */
    val sql: StateFlow<String> = observed
        .map { it.sql }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun onQueryChange(text: String) {
        _queryText.value = text
    }
}
