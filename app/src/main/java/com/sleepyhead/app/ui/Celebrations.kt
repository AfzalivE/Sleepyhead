package com.sleepyhead.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Short, warm messages. They change with your progress and your streak. */
object Encouragement {

    private val stepCheers = listOf(
        "Nice work.", "That's one.", "Good start.", "Look at you go.", "Up and moving.", "Keep it rolling.",
    )

    fun step(title: String, remaining: Int, seed: Int): String {
        val cheer = stepCheers[Math.floorMod(seed, stepCheers.size)]
        return when (remaining) {
            0 -> "All done. Good morning!"
            1 -> "$cheer \"$title\" is done. One step left."
            else -> "$cheer \"$title\" is done. $remaining steps left."
        }
    }

    /** The big line on the done screen. Milestones get their own words. */
    fun doneHeadline(streak: Int): String = when {
        streak >= 100 -> "100 mornings. Legendary."
        streak >= 50 -> "50 mornings in a row. Unstoppable."
        streak >= 30 -> "A whole month of good mornings!"
        streak >= 21 -> "Three weeks strong. This is a habit now."
        streak >= 14 -> "Two weeks in a row. Amazing."
        streak >= 10 -> "Ten in a row. You're on fire."
        streak >= 7 -> "A full week of good mornings!"
        streak >= 5 -> "Five in a row. Keep it going."
        streak >= 3 -> "Three in a row. It's sticking."
        streak == 2 -> "Two in a row. Nice."
        else -> "You're at your desk."
    }

    fun isMilestone(streak: Int) = streak in setOf(3, 5, 7, 10, 14, 21, 30, 50, 100) || (streak > 100 && streak % 50 == 0)

    fun streakLine(streak: Int, best: Int): String = when {
        streak <= 0 -> ""
        streak > 1 && streak >= best -> "New best streak!"
        best > streak -> "Your best is $best. ${best - streak} more to beat it."
        else -> ""
    }
}

/** A short "well done" buzz. */
fun celebrateHaptic(view: View) {
    val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
    view.performHapticFeedback(c)
}

/** A sun that rises and grows into place, with slowly turning rays. */
@Composable
fun RisingSun(modifier: Modifier = Modifier, animate: Boolean = true) {
    val sun = MaterialTheme.colorScheme.primary
    val rise = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow))
    }
    val spin by rememberInfiniteTransition(label = "rays").animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    Canvas(
        modifier
            .size(120.dp)
            .graphicsLayer {
                translationY = (1f - rise.value) * 120.dp.toPx()
                alpha = rise.value.coerceIn(0f, 1f)
            }
    ) {
        val c = center
        val r = size.minDimension * 0.22f
        rotate(spin, c) {
            for (i in 0 until 12) {
                val a = (i * 30.0) * PI / 180
                val inner = r * 1.35f
                val outer = r * (if (i % 2 == 0) 2.0f else 1.75f)
                drawLine(
                    sun.copy(alpha = 0.85f),
                    Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                    Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(sun, r, c)
    }
}

/** Counts up from 0 to [value], for streak numbers. */
@Composable
fun CountUp(value: Int) {
    val shown by animateIntAsState(value, tween(900, delayMillis = 300, easing = FastOutSlowInEasing), label = "count")
    Text("$shown", fontSize = 56.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onBackground)
}

private class Piece(
    val x: Float, val speed: Float, val drift: Float, val spin: Float,
    val size: Float, val color: Color, val delay: Float,
)

/** Confetti that falls once across the screen. Change [key] to play it again. */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier.fillMaxSize(), count: Int = 70) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
        Color(0xFFE8ECF4), Color(0xFF9AD0A8), Color(0xFFE59AA8),
    )
    val pieces = remember(key) {
        val r = Random(key.hashCode())
        List(count) {
            Piece(
                x = r.nextFloat(), speed = 0.6f + r.nextFloat() * 0.6f, drift = (r.nextFloat() - 0.5f) * 0.25f,
                spin = (r.nextFloat() - 0.5f) * 720f, size = 6f + r.nextFloat() * 8f,
                color = colors[r.nextInt(colors.size)], delay = r.nextFloat() * 0.25f,
            )
        }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier) {
        pieces.forEach { p ->
            val local = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20f + local * p.speed * size.height * 1.2f
            val x = (p.x + p.drift * sin(local * 6f)) * size.width
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            val s = p.size.dp.toPx() / 2
            rotate(p.spin * local, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - s, y - s / 2), Size(s * 2, s))
            }
        }
    }
}
