package com.roomql.demo.ui.typeahead

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roomql.demo.ui.components.DemoTopBar
import com.roomql.demo.ui.components.EmptyState
import com.roomql.demo.ui.components.ProductRow
import com.roomql.demo.ui.components.SqlPanel

@Composable
fun TypeAheadScreen(onBack: () -> Unit, viewModel: TypeAheadViewModel = viewModel()) {
    val text by viewModel.queryText.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val sql by viewModel.sql.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { DemoTopBar(title = "Search as you type", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { OutlinedTextField(
                value = text,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Product name contains") },
                placeholder = { Text("try \"atlas\" or \"pro\"") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            ) }

            item { Text(
                "Debounced input feeding contains, returning a Flow. It keeps re-emitting " +
                    "only because the DAO declares observedEntities — omit that and the " +
                    "Flow emits once, then goes silent forever.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) }

            item { Text("${results.size} rows", style = MaterialTheme.typography.titleSmall) }

            item { SqlPanel(sql = sql, caption = "SQL executed") }

            if (results.isEmpty()) {
                item { EmptyState("Nothing matches \"$text\".") }
            } else {
                items(results, key = { it.id }) { product ->
                    ProductRow(
                        title = product.name,
                        subtitle = "${product.category} · ${"%.2f".format(product.price)}",
                    )
                }
            }
        }
    }
}
