package com.trackcats.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackcats.app.R
import com.trackcats.app.core.Rarity

val Ink = Color(0xFF1B1F2A)
val Amber = Color(0xFFFFB627)

/** Chunky display lettering (Lilita One, SIL Open Font License) for titles, buttons and big results. */
val GameFont = FontFamily(Font(R.font.lilita_one))

const val DefaultThemeColor = 0xFFE8590C.toInt()

@Composable
fun isDarkTheme(): Boolean = isSystemInDarkTheme()

/**
 * Builds the app's colours around one accent colour chosen by the player. The accent is nudged
 * darker (light theme) or lighter (dark theme) when needed so text in it stays readable.
 */
fun schemeFor(accent: Color, dark: Boolean): ColorScheme {
    val f = FloatArray(3).also { android.graphics.Color.colorToHSV(accent.toArgb(), it) }
    val h = f[0]
    val s = f[1]
    var v = f[2]
    if (!dark) {
        while (v > 0.3f && hsv(h, s, v).luminance() > 0.36f) v -= 0.03f
        val primary = hsv(h, s, v)
        return lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = hsv(h, s * 0.22f, 1f),
            onPrimaryContainer = hsv(h, s, 0.28f),
            secondary = Color(0xFF1971C2),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFF0C2),
            onSecondaryContainer = Color(0xFF3B2A00),
            tertiary = Color(0xFF2B8A3E),
            background = hsv(h, s * 0.08f, 0.98f),
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFE7EDF4),
            onSurfaceVariant = Color(0xFF55606E),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7FAFD),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFF7FAFD),
            surfaceContainerHighest = Color(0xFFEEF3F8),
            outline = Color(0xFF8A96A6),
            outlineVariant = Color(0xFFD5DDE7),
        )
    }
    v = v.coerceAtLeast(0.85f)
    var sat = s.coerceAtMost(0.75f)
    while (sat > 0f && hsv(h, sat, v).luminance() < 0.32f) sat -= 0.04f
    return darkColorScheme(
        primary = hsv(h, sat, v),
        onPrimary = hsv(h, s.coerceAtMost(0.8f), 0.16f),
        primaryContainer = hsv(h, s * 0.8f, 0.34f),
        onPrimaryContainer = hsv(h, s * 0.25f, 1f),
        secondary = Color(0xFF74B6F2),
        onSecondary = Color(0xFF002B4D),
        secondaryContainer = Color(0xFF45380F),
        onSecondaryContainer = Color(0xFFFFE9A8),
        tertiary = Color(0xFF69DB7C),
        background = Color(0xFF0F1420),
        onBackground = Color(0xFFE6EBF3),
        surface = Color(0xFF1B2232),
        onSurface = Color(0xFFE6EBF3),
        surfaceVariant = Color(0xFF263043),
        onSurfaceVariant = Color(0xFFB4BFCE),
        surfaceContainerLowest = Color(0xFF141A27),
        surfaceContainerLow = Color(0xFF1B2232),
        surfaceContainer = Color(0xFF1B2232),
        surfaceContainerHigh = Color(0xFF222B3D),
        surfaceContainerHighest = Color(0xFF2A3447),
        outline = Color(0xFF6B788C),
        outlineVariant = Color(0xFF323D52),
    )
}

private val base = Typography()

/** Titles and button labels use the display font; body text keeps the system font for readability. */
private val AppType = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = GameFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = GameFont),
    headlineMedium = base.headlineMedium.copy(fontFamily = GameFont),
    headlineSmall = base.headlineSmall.copy(fontFamily = GameFont),
    titleLarge = base.titleLarge.copy(fontFamily = GameFont, fontSize = 24.sp, letterSpacing = 0.3.sp),
    titleMedium = base.titleMedium.copy(fontFamily = GameFont, fontSize = 19.sp, letterSpacing = 0.3.sp),
    labelLarge = base.labelLarge.copy(fontFamily = GameFont, fontSize = 16.sp, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TrackCatsTheme(accent: Int? = null, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = remember(accent, dark) { schemeFor(Color(accent ?: DefaultThemeColor), dark) }
    MaterialTheme(colorScheme = scheme, typography = AppType, shapes = AppShapes, content = content)
}

/** Soft gradient with a faint dot pattern, drawn behind every screen. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    // The background picks up a soft tint of the chosen theme colour.
    val accent = MaterialTheme.colorScheme.primary
    val f = remember(accent) { FloatArray(3).also { android.graphics.Color.colorToHSV(accent.toArgb(), it) } }
    val colors = if (dark) listOf(hsv(f[0], f[1] * 0.55f, 0.20f), Color(0xFF0F1420), hsv(f[0] + 40f, f[1] * 0.45f, 0.15f))
    else listOf(hsv(f[0], f[1] * 0.20f, 1f), hsv(f[0], f[1] * 0.05f, 0.98f), hsv(f[0] + 40f, f[1] * 0.16f, 1f))
    val dot = if (dark) Color.White.copy(alpha = 0.035f) else accent.copy(alpha = 0.07f)
    val step = with(LocalDensity.current) { 26.dp.toPx() }
    val radius = with(LocalDensity.current) { 2.5.dp.toPx() }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        Canvas(Modifier.fillMaxSize()) {
            var row = 0
            var y = step / 2
            while (y < size.height) {
                var x = if (row % 2 == 0) step / 2 else step
                while (x < size.width) {
                    drawCircle(dot, radius, Offset(x, y))
                    x += step
                }
                y += step * 0.86f
                row++
            }
        }
        content()
    }
}

/**
 * Display lettering with a dark outline, like a game title. The outline layer is hidden from
 * accessibility so the text is announced once.
 */
@Composable
fun OutlinedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fill: Color = Color.White,
    outline: Color = Color(0xFF2A1A0E),
    outlineWidth: Dp = 4.dp,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    val width = with(LocalDensity.current) { outlineWidth.toPx() }
    val shaped = style.copy(fontFamily = GameFont, letterSpacing = 1.2.sp)
    Box(modifier) {
        Text(
            text, Modifier.clearAndSetSemantics { }, color = outline, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign,
            style = shaped.copy(drawStyle = Stroke(width = width, join = StrokeJoin.Round)),
        )
        Text(text, color = fill, style = shaped, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }
}

/** Rarity colours follow the game's convention closely enough to be recognisable. */
fun rarityColor(rarity: Int): Color = when (rarity) {
    Rarity.SUPER -> Color(0xFFE08A00)
    Rarity.UBER -> Color(0xFFD32F2F)
    Rarity.LEGEND -> Color(0xFF7B1FA2)
    else -> Color(0xFF607D8B)
}
