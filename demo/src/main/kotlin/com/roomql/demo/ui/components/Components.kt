package com.roomql.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roomql.demo.R
import com.roomql.demo.ui.theme.LocalCodeColors

/** The RoomQL mark, for the home header and app bars. */
@Composable
fun RoomQlLogo(size: Int = 32, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_roomql_logo),
        contentDescription = "RoomQL",
        tint = androidx.compose.ui.graphics.Color.Unspecified,
        modifier = modifier.size(size.dp),
    )
}

/** Shared app bar so every screen has the same back affordance and title treatment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack == null) {
                    RoomQlLogo(size = 28)
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                } else {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/**
 * The SQL readout.
 *
 * Deliberately a dark code block in both light and dark themes: it is the one element
 * every screen shares, it echoes the brand banner, and monospace-on-dark is how people
 * expect to read SQL.
 */
@Composable
fun SqlPanel(sql: String, caption: String, modifier: Modifier = Modifier) {
    val code = LocalCodeColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(code.background, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = code.label,
            letterSpacing = 1.sp,
        )
        Text(
            sql,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
            ),
            color = code.text,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * A horizontally scrolling chip row with an optional clear action.
 *
 * Tapping the selected chip clears it, and the header shows a Clear control whenever
 * something is active — without it, working out how to get back to "no filter" is the
 * first thing a user struggles with on these screens.
 */
@Composable
fun ChipRow(
    label: String,
    options: List<String>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    onClear: (() -> Unit)? = null,
    hasSelection: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasSelection && onClear != null) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onToggle(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}

/**
 * Horizontally scrolling strategy selector.
 *
 * Deliberately not a SegmentedButtonRow: that control divides the width evenly and clips
 * labels like "Overloaded DAO", which is exactly the text a reader needs to understand
 * what they are looking at. Chips size to their content and scroll instead.
 */
@Composable
fun StrategySelector(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "IMPLEMENTATION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
    }
}

/** A small labelled metric, used for "DAO methods" and similar costs. */
@Composable
fun MetricBadge(value: String, caption: String) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The Kotlin you write for the selected approach.
 *
 * The SQL panel shows what reaches SQLite; this shows what it costs you to get there,
 * which is the actual difference between these four implementations. Without it the demo
 * only proves the statements differ, not that one approach is better to live with.
 */
@Composable
fun CodePanel(code: String, caption: String, modifier: Modifier = Modifier) {
    val colors = LocalCodeColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.label,
            letterSpacing = 1.sp,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = androidx.compose.ui.graphics.Color(0xFFE8E8E8),
            )
        }
    }
}

/** Shown instead of an empty list, so a zero-result query never looks like a crash. */
@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** One catalogue row, used by every list in the app. */
@Composable
fun ProductRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
