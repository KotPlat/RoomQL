package com.roomql.demo.ui.faceted

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roomql.demo.ui.components.ChipRow
import com.roomql.demo.ui.components.DemoTopBar
import com.roomql.demo.ui.components.EmptyState
import com.roomql.demo.ui.components.ProductRow
import com.roomql.demo.ui.components.SqlPanel

@Composable
fun FacetedScreen(onBack: () -> Unit, viewModel: FacetedViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { DemoTopBar(title = "Faceted catalogue", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(
                "Chips drive an IN (...) clause. Selecting none means no filter — not " +
                    "\"match nothing\".",
                style = MaterialTheme.typography.bodyMedium,
            ) }

            item { ChipRow(
                label = "Categories",
                options = state.categories,
                isSelected = { it in state.selectedCategories },
                onToggle = viewModel::toggleCategory,
                onClear = viewModel::clearCategories,
                hasSelection = state.selectedCategories.isNotEmpty(),
            ) }

            item { ChipRow(
                label = "Brand country (joined table)",
                options = state.countries,
                isSelected = { it == state.country },
                onToggle = { viewModel.onCountry(if (it == state.country) null else it) },
                onClear = { viewModel.onCountry(null) },
                hasSelection = state.country != null,
            ) }

            item { Text(
                "${state.results.size} rows · product and brand names both survive the " +
                    "join because RoomQL aliased the colliding columns",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) }

            item { SqlPanel(sql = state.sql, caption = "SQL executed") }

            if (state.results.isEmpty()) {
                item { EmptyState("No products match this combination.") }
            } else {
                items(state.results) { row ->
                    ProductRow(
                        title = row.productName,
                        subtitle = "${row.brandName} · ${row.category} · ${"%.2f".format(row.price)}",
                    )
                }
            }
        }
    }
}
