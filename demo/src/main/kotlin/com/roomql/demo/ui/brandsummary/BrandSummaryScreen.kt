package com.roomql.demo.ui.brandsummary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roomql.demo.data.BrandSummary
import com.roomql.demo.ui.components.DemoTopBar
import com.roomql.demo.ui.components.EmptyState
import com.roomql.demo.ui.components.ProductRow
import com.roomql.demo.ui.components.SqlPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandSummaryScreen(onBack: () -> Unit, viewModel: BrandSummaryViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { DemoTopBar(title = "Brand summary", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "One row per brand: groupBy + select + count/avg, joined for the real " +
                        "name — where { }, groupBy(...), and select(...) composing in one query.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                FilterChip(
                    selected = state.inStockOnly,
                    onClick = viewModel::toggleInStockOnly,
                    label = { Text("In stock only") },
                )
            }

            item { SqlPanel(sql = state.sql, caption = "SQL executed") }

            if (state.rows.isEmpty()) {
                item { EmptyState("No products match this combination.") }
            } else {
                items(state.rows, key = { it.brandName }) { row -> BrandSummaryRow(row) }
            }
        }
    }
}

@Composable
private fun BrandSummaryRow(row: BrandSummary) {
    ProductRow(
        title = row.brandName,
        subtitle = "${row.productCount} products · avg ${"%.2f".format(row.avgPrice)}",
    )
}
