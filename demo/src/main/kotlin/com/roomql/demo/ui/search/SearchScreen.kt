package com.roomql.demo.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roomql.demo.ui.components.ChipRow
import com.roomql.demo.ui.components.CodePanel
import com.roomql.demo.ui.components.DemoTopBar
import com.roomql.demo.ui.components.EmptyState
import com.roomql.demo.ui.components.MetricBadge
import com.roomql.demo.ui.components.ProductRow
import com.roomql.demo.ui.components.SqlPanel
import com.roomql.demo.ui.components.StrategySelector

/**
 * The flagship demo: four optional filters, four implementations, one result set.
 *
 * The whole screen is a single LazyColumn. Filters, the code panel, the SQL panel and the
 * results all scroll together — a fixed header with a scrolling list underneath left the
 * results clipped on shorter screens.
 */
@Composable
fun SearchScreen(onBack: () -> Unit, viewModel: SearchViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strategy = viewModel.currentStrategy()

    Scaffold(
        topBar = { DemoTopBar(title = "Multi-filter search", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Same filters, same rows — four different implementations. " +
                        "Switch below and compare what you write, not just what runs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                StrategySelector(
                    labels = viewModel.strategyLabels,
                    selectedIndex = state.strategyIndex,
                    onSelect = viewModel::onStrategy,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBadge(
                        value = strategy.daoMethods.toString(),
                        caption = if (strategy.daoMethods == 1) "DAO method" else "DAO methods",
                    )
                    MetricBadge(value = "${state.products.size}", caption = "rows returned")
                    MetricBadge(value = "${state.filters.activeCount}/4", caption = "filters active")
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strategy.summary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        strategy.safety,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // The point of the whole screen: what this approach costs you to write.
            item { CodePanel(code = strategy.callSite, caption = "What you write") }

            item { SqlPanel(sql = state.sql, caption = "What SQLite runs") }

            item { HorizontalDivider() }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Filters", style = MaterialTheme.typography.titleSmall)
                    if (state.filters.activeCount > 0) {
                        AssistChip(onClick = viewModel::clearFilters, label = { Text("Reset all") })
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow(
                        label = "Category",
                        options = state.categories,
                        isSelected = { it == state.filters.category },
                        onToggle = { viewModel.onCategory(if (it == state.filters.category) null else it) },
                    )
                    ChipRow(
                        label = "Min price",
                        options = listOf("250", "500", "750"),
                        isSelected = { it.toDouble() == state.filters.minPrice },
                        onToggle = {
                            val v = it.toDouble()
                            viewModel.onMinPrice(if (v == state.filters.minPrice) null else v)
                        },
                    )
                    ChipRow(
                        label = "Min rating",
                        options = listOf("3.0", "4.0", "4.5"),
                        isSelected = { it.toDouble() == state.filters.minRating },
                        onToggle = {
                            val v = it.toDouble()
                            viewModel.onMinRating(if (v == state.filters.minRating) null else v)
                        },
                    )
                    ChipRow(
                        label = "Availability",
                        options = listOf("In stock only"),
                        isSelected = { state.filters.inStockOnly == true },
                        onToggle = {
                            viewModel.onInStockOnly(if (state.filters.inStockOnly == true) null else true)
                        },
                    )
                }
            }

            item {
                Text(
                    "${state.products.size} results",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (state.products.isEmpty() && !state.loading) {
                item { EmptyState("No products match these filters.\nTry Reset all.") }
            } else {
                items(state.products, key = { it.id }) { product ->
                    ProductRow(
                        title = product.name,
                        subtitle = "${product.category} · ${"%.2f".format(product.price)} · " +
                            "${product.rating}★${if (product.inStock) "" else " · out of stock"}",
                    )
                }
            }
        }
    }
}
