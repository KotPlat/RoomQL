package com.roomql.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.roomql.demo.ui.components.DemoTopBar
import com.roomql.demo.ui.components.RoomQlLogo
import com.roomql.demo.ui.faceted.FacetedScreen
import com.roomql.demo.ui.search.SearchScreen
import com.roomql.demo.ui.sorted.SortedScreen
import com.roomql.demo.ui.theme.RoomQlBrand
import com.roomql.demo.ui.typeahead.TypeAheadScreen

@Composable
fun DemoApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onDemoClick = { navController.navigate(it.route) })
        }
        composable(Demo.Search.route) {
            SearchScreen(onBack = { navController.popBackStack() })
        }
        composable(Demo.Faceted.route) {
            FacetedScreen(onBack = { navController.popBackStack() })
        }
        composable(Demo.Sorted.route) {
            SortedScreen(onBack = { navController.popBackStack() })
        }
        composable(Demo.TypeAhead.route) {
            TypeAheadScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun HomeScreen(onDemoClick: (Demo) -> Unit) {
    Scaffold(
        topBar = { DemoTopBar(title = "RoomQL") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Banner() }
            items(Demo.entries) { demo ->
                DemoCard(demo = demo, onClick = { onDemoClick(demo) })
            }
            item {
                Text(
                    "Every screen builds its SQL at runtime and shows you the statement " +
                        "it ran. Nothing here is pre-written.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** Brand header: the mark plus the one-line pitch, echoing the README banner. */
@Composable
private fun Banner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(RoomQlBrand.Ink, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoomQlLogo(size = 52)
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                "Dynamic Room queries",
                style = MaterialTheme.typography.titleMedium,
                color = RoomQlBrand.Green,
            )
            Text(
                "Type-safe filters that disappear when null — no raw SQL, no 2ⁿ DAO methods.",
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color(0xFFBDBDBD),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DemoCard(demo: Demo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    demo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                demo.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
