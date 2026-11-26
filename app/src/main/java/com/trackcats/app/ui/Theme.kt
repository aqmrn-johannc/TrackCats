package com.trackcats.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.trackcats.app.core.Rarity

val BrandYellow = Color(0xFFFCDB02)
val Ink = Color(0xFF1C1A12)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = BrandYellow,
    primaryContainer = BrandYellow,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF6A5D00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF2A8),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFFFFBEC),
    onBackground = Ink,
    surface = Color(0xFFFFFBEC),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1EBD2),
    onSurfaceVariant = Color(0xFF4C4733),
    surfaceContainer = Color(0xFFF7F1DA),
    surfaceContainerHigh = Color(0xFFF1EBD2),
    surfaceContainerLow = Color(0xFFFBF6E2),
    outline = Color(0xFF7D775F),
)

private val Dark = darkColorScheme(
    primary = BrandYellow,
    onPrimary = Ink,
    primaryContainer = Color(0xFF524600),
    onPrimaryContainer = Color(0xFFFFF2A8),
    secondary = Color(0xFFD9C85A),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3F3A1E),
    onSecondaryContainer = Color(0xFFF3E9B5),
    tertiary = Color(0xFF81C784),
    background = Color(0xFF15140E),
    onBackground = Color(0xFFEAE3CB),
    surface = Color(0xFF15140E),
    onSurface = Color(0xFFEAE3CB),
    surfaceVariant = Color(0xFF34311F),
    onSurfaceVariant = Color(0xFFCEC7A9),
    surfaceContainer = Color(0xFF211F16),
    surfaceContainerHigh = Color(0xFF2B291D),
    surfaceContainerLow = Color(0xFF1C1B13),
    outline = Color(0xFF979176),
)

@Composable
fun TrackCatsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Rarity colours follow the game's convention closely enough to be recognisable. */
fun rarityColor(rarity: Int): Color = when (rarity) {
    Rarity.SUPER -> Color(0xFFE08A00)
    Rarity.UBER -> Color(0xFFD32F2F)
    Rarity.LEGEND -> Color(0xFF7B1FA2)
    else -> Color(0xFF607D8B)
}
