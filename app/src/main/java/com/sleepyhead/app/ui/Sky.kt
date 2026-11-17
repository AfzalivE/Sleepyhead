package com.sleepyhead.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.Insights
import com.sleepyhead.app.data.Routine
import java.time.LocalDateTime

/** Fixed colours for the sky and the history squares. */
object Palette {
    val Sun = Color(0xFFF2B66D)
    val SunLight = Color(0xFFFFD99B)
    val SunDim = Color(0xFFC08A4E)
    val SunAfter = Color(0xFF7E6043)
    val Away = Color(0xFF5B6E99)
    val LockOff = Color(0xFFB5525C)
    val Skipped = Color(0xFF3A4152)
    val Empty = Color(0xFF1E2430)
    val Ink = Color(0xFF2A1B05)

    private val night = listOf(Color(0xFF10141D), Color(0xFF141A27), Color(0xFF181E2E), Color(0xFF1C2236))
    private val dawn = listOf(Color(0xFF141A2B), Color(0xFF202844), Color(0xFF4B3D58), Color(0xFFA9705A))
    private val sunrise = listOf(Color(0xFF3B4A7A), Color(0xFFC47A6A), Color(0xFFF2B66D), Color(0xFFFFD99B))

    /** 0 = night, 1 = dawn (last step left), 2 = full sunrise (done). */
    fun sky(p: Float): List<Color> = when {
        p <= 1f -> night.zip(dawn) { a, b -> lerp(a, b, p.coerceIn(0f, 1f)) }
        else -> dawn.zip(sunrise) { a, b -> lerp(a, b, (p - 1f).coerceIn(0f, 1f)) }
    }
}

/** How far the sky has come: 0 at night, up to 1 as you do steps, 2 when the morning is done. */
fun skyLevel(data: AppData, now: LocalDateTime): Float {
    if (data.steps.isEmpty() || !Routine.inMorningWindow(data.settings, now)) return 0f
    val log = data.logs[now.toLocalDate().toString()]
    if (log?.completedAtMillis != null) return 2f
    if (log?.skipped == true) return 0f
    val done = log?.done?.size ?: 0
    // Lock start already shows the first light. Each step adds more.
    return 0.25f + 0.75f * done / data.steps.size
}

/** Full-screen sky behind the content, with a sun that rises as the level grows. */
@Composable
fun SkyBackground(level: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    val p by animateFloatAsState(level, tween(1600), label = "sky")
    val colors = Palette.sky(p)
    Box(modifier.background(Brush.verticalGradient(colors))) {
        if (p > 0.05f) {
            Canvas(Modifier.fillMaxSize()) {
                val r = if (p <= 1f) size.width * 0.34f else size.width * (0.34f - 0.1f * (p - 1f))
                // From below the bottom edge (night) to just above it (dawn), then high in the sky (done).
                val y = if (p <= 1f) size.height + r * (1.1f - 0.65f * p)
                else (size.height + r * 0.45f) - (size.height * 0.75f) * (p - 1f)
                val c = Offset(size.width / 2, y)
                val a = (p * 1.4f).coerceIn(0f, 1f)
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFFFFF3D6).copy(alpha = a),
                        0.4f to Palette.SunLight.copy(alpha = a),
                        0.62f to Palette.Sun.copy(alpha = a * 0.9f),
                        1f to Palette.Sun.copy(alpha = 0f),
                        center = c, radius = r * 1.45f,
                    ),
                    radius = r * 1.45f, center = c,
                )
            }
        }
    }
}

/** Dark ink on the bright sunrise, so text stays easy to read. */
@Composable
fun SunriseInk(on: Boolean, content: @Composable () -> Unit) {
    if (!on) return content()
    val cs = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = cs.copy(
            onBackground = Palette.Ink,
            onSurface = Palette.Ink,
            onSurfaceVariant = Color(0xFF5A3E1C),
            primary = Color(0xFF6B3F0F),
            secondary = Color(0xFF4A3A5C),
        ),
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalContentColor provides Palette.Ink, content = content)
    }
}

fun cellColor(c: Insights.Cell): Color = when (c) {
    Insights.Cell.Early -> Palette.SunLight
    Insights.Cell.Mid -> Palette.Sun
    Insights.Cell.Late -> Palette.SunDim
    Insights.Cell.AfterEnd -> Palette.SunAfter
    Insights.Cell.Away -> Palette.Away
    Insights.Cell.LockOff -> Palette.LockOff
    Insights.Cell.Skipped -> Palette.Skipped
    Insights.Cell.Missed -> Color.Transparent
    Insights.Cell.Pending -> Color(0xFF181C26)
    Insights.Cell.Off -> Palette.Empty
}

/** One square of the history grids. Missed and not-yet days are outlines. */
fun Modifier.cellSquare(c: Insights.Cell, corner: Dp = 4.dp, selected: Boolean = false): Modifier {
    val shape = RoundedCornerShape(corner)
    val base = when (c) {
        // Missed is a clear outline. Not-yet days are a faint fill, so the two look different.
        Insights.Cell.Missed -> border(1.5.dp, Color(0xFF6A7388), shape)
        else -> background(cellColor(c), shape)
    }
    return if (selected) base.border(2.dp, Color.White, shape) else base
}

fun cellLabel(c: Insights.Cell): String = when (c) {
    Insights.Cell.Early, Insights.Cell.Mid, Insights.Cell.Late -> "On time"
    Insights.Cell.AfterEnd -> "Done after lock end"
    Insights.Cell.Away -> "Away from home"
    Insights.Cell.LockOff -> "Lock was off"
    Insights.Cell.Skipped -> "Skipped"
    Insights.Cell.Missed -> "Not finished"
    Insights.Cell.Off -> "Day off"
    Insights.Cell.Pending -> "Not yet"
}

/** The app icon's sun over the horizon, for the wordmark and onboarding. */
@Composable
fun SunMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(com.sleepyhead.app.R.drawable.ic_launcher_foreground),
        contentDescription = null,
        // The drawable has a wide safe area. Scale it up so the mark fills the box.
        modifier = modifier.graphicsLayer { scaleX = 1.7f; scaleY = 1.7f },
    )
}

