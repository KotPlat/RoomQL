package com.roomql.demo.ui.sorted

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roomql.runtime.SortDirection
import com.roomql.demo.data.SortColumn
import com.roomql.demo.ui.components.ChipRow
import com.roomql.demo.ui.components.DemoTopBar
import com.roomql.demo.ui.components.EmptyState
import com.roomql.demo.ui.components.ProductRow
import com.roomql.demo.ui.components.SqlPanel

@Composable
fun SortedScreen(onBack: () -> Unit, viewModel: SortedViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { DemoTopBar(title = "Sortable, paginated", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Sort column and direction are chosen here and passed to orderBy at " +
                        "runtime — the one case the IS NULL OR trick cannot express at all, " +
                        "because a column name is not a bindable parameter.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                ChipRow(
                    label = "Sort by",
                    options = SortColumn.entries.map { it.label },
                    isSelected = { it == state.sortColumn.label },
                    onToggle = { label ->
                        SortColumn.entries.firstOrNull { it.label == label }
                            ?.let(viewModel::onSortColumn)
                    },
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = viewModel::toggleDirection) {
                        Text(if (state.direction == SortDirection.ASC) "Ascending ↑" else "Descending ↓")
                    }
                    Text("Page ${state.page + 1}", style = MaterialTheme.typography.titleSmall)
                }
            }

            item { SqlPanel(sql = state.sql, caption = "SQL executed") }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = viewModel::previousPage,
                        enabled = state.page > 0,
                    ) { Text("Previous") }
                    Button(
                        onClick = viewModel::nextPage,
                        enabled = state.products.size == state.pageSize,
                    ) { Text("Next") }
                }
            }

            if (state.products.isEmpty()) {
                item { EmptyState("No more rows on this page.") }
            } else {
                items(state.products, key = { it.id }) { product ->
                    ProductRow(
                        title = product.name,
                        subtitle = "${product.category} · ${"%.2f".format(product.price)} · ${product.rating}★",
                    )
                }
            }
        }
    }
}
