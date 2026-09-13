package com.roomql.demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * RoomQL brand palette, taken from docs/banner.svg and docs/icon.svg so the app, the
 * README banner, and the launcher icon all read as one product.
 */
object RoomQlBrand {
    /** The Android green that carries the whole brand. */
    val Green = Color(0xFF3DDC84)

    /** Banner background — the deepest surface. */
    val Ink = Color(0xFF161616)

    /** Icon tile — one step up from Ink. */
    val Tile = Color(0xFF1C1C1E)

    /** Darkened green with enough contrast to sit on white. */
    val GreenDeep = Color(0xFF1B7F4B)
}

/** Colours for the SQL readout, which stays dark in both themes — it is a code block. */
data class CodeColors(val background: Color, val text: Color, val label: Color)

val LocalCodeColors = staticCompositionLocalOf {
    CodeColors(RoomQlBrand.Ink, RoomQlBrand.Green, Color(0xFF9E9E9E))
}

private val DarkColors = darkColorScheme(
    primary = RoomQlBrand.Green,
    onPrimary = Color(0xFF08150D),
    primaryContainer = Color(0xFF143D28),
    secondary = RoomQlBrand.Green,
    // FilterChip's selected state uses secondaryContainer; without these it renders in
    // Material's default purple, which reads as "not our brand" on every filter row.
    secondaryContainer = Color(0xFF1E4D34),
    onSecondaryContainer = RoomQlBrand.Green,
    background = RoomQlBrand.Ink,
    surface = RoomQlBrand.Tile,
    onBackground = Color(0xFFECECEC),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF242427),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF3A3A3D),
)

private val LightColors = lightColorScheme(
    primary = RoomQlBrand.GreenDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3DF),
    onPrimaryContainer = Color(0xFF06281A),
    secondary = RoomQlBrand.GreenDeep,
    secondaryContainer = Color(0xFFCFF3DF),
    onSecondaryContainer = Color(0xFF06281A),
    background = Color(0xFFFBFBFA),
    surface = Color.White,
    onBackground = Color(0xFF1A1C1A),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFEFF1EE),
    onSurfaceVariant = Color(0xFF44474A),
    outline = Color(0xFFC6C9C4),
)

@Composable
fun RoomQlDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val codeColors = CodeColors(
        background = RoomQlBrand.Ink,
        text = RoomQlBrand.Green,
        label = Color(0xFF9E9E9E),
    )
    CompositionLocalProvider(LocalCodeColors provides codeColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
