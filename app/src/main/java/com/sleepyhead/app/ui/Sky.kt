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
import androidx.compose.ui.composed
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

/** Square colours come from the theme (Material You: your wallpaper colours). Earlier finish = stronger colour. */
@Composable
fun cellColor(c: Insights.Cell): Color {
    val cs = MaterialTheme.colorScheme
    return when (c) {
        Insights.Cell.Early -> cs.primary
        Insights.Cell.Mid -> cs.primary.copy(alpha = 0.7f)
        Insights.Cell.Late -> cs.primary.copy(alpha = 0.45f)
        Insights.Cell.AfterEnd -> cs.primary.copy(alpha = 0.25f)
        Insights.Cell.Away -> cs.tertiary
        Insights.Cell.LockOff -> cs.error
        Insights.Cell.Skipped -> cs.outlineVariant
        Insights.Cell.Missed -> Color.Transparent
        Insights.Cell.Pending -> cs.surfaceContainer
        Insights.Cell.Off -> cs.surfaceContainerHighest
    }
}

/** One square of the history grids. Missed days are an outline. */
fun Modifier.cellSquare(c: Insights.Cell, corner: Dp = 4.dp, selected: Boolean = false): Modifier = composed {
    val shape = RoundedCornerShape(corner)
    val cs = MaterialTheme.colorScheme
    val base = if (c == Insights.Cell.Missed) border(1.5.dp, cs.outline, shape) else background(cellColor(c), shape)
    if (selected) base.border(2.dp, cs.onSurface, shape) else base
}

/** Text colour on a square, so day numbers stay readable. */
@Composable
fun onCellColor(c: Insights.Cell): Color {
    val cs = MaterialTheme.colorScheme
    return when (c) {
        Insights.Cell.Early, Insights.Cell.Mid -> cs.onPrimary
        Insights.Cell.Away -> cs.onTertiary
        Insights.Cell.LockOff -> cs.onError
        Insights.Cell.Pending -> cs.outline
        else -> cs.onSurfaceVariant
    }
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
