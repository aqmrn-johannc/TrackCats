package com.trackcats.app.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

fun hsv(hue: Float, saturation: Float, value: Float): Color =
    Color(AColor.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

private val presets = listOf(
    "Orange" to DefaultThemeColor, "Red" to 0xFFD6336C.toInt(), "Purple" to 0xFF7048E8.toInt(), "Blue" to 0xFF1C7ED6.toInt(),
    "Teal" to 0xFF0C8599.toInt(), "Green" to 0xFF2F9E44.toInt(), "Gold" to 0xFFE0A100.toInt(), "Grey" to 0xFF495057.toInt(),
)

/**
 * Colour wheel: the angle picks the hue and the distance from the centre picks how strong the
 * colour is. Brightness is set with the slider under it.
 */
@Composable
fun ColorWheel(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val rainbow = remember { (0..360 step 30).map { hsv(it.toFloat(), 1f, 1f) } }
    Canvas(
        modifier
            .semantics { contentDescription = "Colour wheel" }
            .pointerInput(Unit) {
                fun pick(p: Offset) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = min(cx, cy)
                    val angle = Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
                    onChange((angle + 360f) % 360f, (hypot(p.x - cx, p.y - cy) / radius).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        pick(change.position)
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(rainbow, center), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius)
        drawCircle(Color.Black.copy(alpha = 1f - value), radius)
        val a = Math.toRadians(hue.toDouble())
        val thumb = Offset(center.x + (cos(a) * saturation * radius).toFloat(), center.y + (sin(a) * saturation * radius).toFloat())
        drawCircle(Color.White, 13.dp.toPx(), thumb)
        drawCircle(hsv(hue, saturation, value), 10.dp.toPx(), thumb)
        drawCircle(Color.Black.copy(alpha = 0.5f), 13.dp.toPx(), thumb, style = Stroke(1.5.dp.toPx()))
    }
}

/** Lets the player choose the app's accent colour. Null means the standard orange. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeColorDialog(current: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { AColor.colorToHSV(current ?: DefaultThemeColor, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2].coerceAtLeast(0.3f)) }
    val color = hsv(hue, saturation, value)
    // The whole dialog previews the colour being chosen.
    MaterialTheme(colorScheme = schemeFor(color, isDarkTheme())) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme colour") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ColorWheel(hue, saturation, value, { h, s -> hue = h; saturation = s }, Modifier.size(220.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Brightness", Modifier.padding(end = 8.dp))
                    Slider(value, { value = it }, Modifier.weight(1f), valueRange = 0.3f..1f)
                }
                // A live sample of how buttons and titles will look.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}) { Text("I rolled") }
                    Text("Card title", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((name, argb) in presets) Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(argb))
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .semantics { contentDescription = name }
                            .clickable {
                                val f = FloatArray(3).also { AColor.colorToHSV(argb, it) }
                                hue = f[0]; saturation = f[1]; value = f[2].coerceAtLeast(0.3f)
                            },
                    )
                }
                TextButton(onClick = { onPick(null) }) { Text("Back to the standard orange") }
            }
        },
        confirmButton = { Button(onClick = { onPick(color.toArgb()) }) { Text("Use this colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
    )
    }
}
