package com.sleepyhead.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.Insights
import com.sleepyhead.app.data.Routine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private enum class HistoryTab(val label: String) { Weeks("16 weeks"), Year("Year"), Month("Month") }

private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val shortTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun HistoryScreen(data: AppData, onBack: () -> Unit, modifier: Modifier) {
    val zone = ZoneId.systemDefault()
    val now = remember { LocalDateTime.now() }
    val today = now.toLocalDate()
    var tab by rememberSaveable { mutableStateOf(HistoryTab.Weeks) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        TopBar("Your mornings", onBack)
        Segmented(tab) { tab = it; selected = null }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            HistoryTab.Weeks -> {
                StreakHero(data, today)
                Spacer(Modifier.height(12.dp))
                StatsRow(data, now, zone)
                SectionTitle("LAST 16 WEEKS", "Year ›") { tab = HistoryTab.Year; selected = null }
                WeekGrid(data, now, zone, selected) { selected = if (selected == it) null else it }
                Legend()
                selected?.let { DayDetail(data, it, now, zone) }
                SectionTitle("TIME TO DESK", "last 30 days", null)
                TimeToDesk(data, today, zone)
            }
            HistoryTab.Year -> {
                Stepper("$year", canNext = year < today.year, onPrev = { year--; selected = null }, onNext = { year++; selected = null })
                val sum = Insights.yearSummary(data, year, now, zone)
                Text(
                    "${sum.good} good mornings · ${sum.away} away",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                selected?.let { DayDetail(data, it, now, zone) }
                YearGrid(data, year, now, zone, selected) { selected = if (selected == it) null else it }
                Legend()
            }
            HistoryTab.Month -> {
                val title = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year
                Stepper(title, canNext = month < YearMonth.from(today),
                    onPrev = { month = month.minusMonths(1); selected = null },
                    onNext = { month = month.plusMonths(1); selected = null })
                MonthGrid(data, month, now, zone, selected) { selected = if (selected == it) null else it }
                Legend()
                selected?.let { DayDetail(data, it, now, zone) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Segmented(tab: HistoryTab, onSelect: (HistoryTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        HistoryTab.entries.forEach { t ->
            val on = t == tab
            Text(
                t.label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Stepper(title: String, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text("‹", fontSize = 24.sp) }
        Text(title, fontFamily = Palette.Serif, fontSize = 24.sp)
        TextButton(onClick = onNext, enabled = canNext) { Text("›", fontSize = 24.sp) }
    }
}

@Composable
private fun StreakHero(data: AppData, today: LocalDate) {
    val streak = Routine.streak(data, today)
    val best = Insights.bestRun(data)
    val next = Insights.nextMilestone(streak)
    val prev = Insights.previousMilestone(streak)
    val fill = if (next > prev) (streak - prev).toFloat() / (next - prev) else 0f
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF2A2335), MaterialTheme.colorScheme.surfaceContainer)), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceContainerHigh
            Canvas(Modifier.size(96.dp)) {
                val w = 7.dp.toPx()
                val s = Size(size.width - w, size.height - w)
                val o = Offset(w / 2, w / 2)
                drawArc(track, 0f, 360f, false, o, s, style = Stroke(w))
                drawArc(Palette.Sun, -90f, 360f * fill, false, o, s, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("$streak", fontFamily = Palette.Serif, fontSize = 38.sp)
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(
                when (streak) {
                    0 -> "No streak yet"
                    1 -> "1 good morning"
                    else -> "$streak good mornings in a row"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            val togo = next - streak
            val line = buildString {
                append(if (togo == 1) "1 more" else "$togo more")
                append(" to reach ${Insights.milestoneName(next)}.")
                if (best != null && best.length > 0) {
                    append("\nBest: ${best.length}")
                    if (best.length > streak) {
                        append(" (in ${best.end.month.getDisplayName(TextStyle.FULL, Locale.getDefault())})")
                    }
                }
            }
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatsRow(data: AppData, now: LocalDateTime, zone: ZoneId) {
    val today = now.toLocalDate()
    val pct = Insights.onTimePercent(data, now, zone)
    val usual = Insights.usualDeskTime(data, today, zone)
    val avg = Insights.average(Insights.timeToDeskSeries(data, today, zone).map { it.second })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(pct?.let { "$it%" } ?: "–", "on time, 30 days", Modifier.weight(1f))
        StatCard(usual?.format(shortTime) ?: "–", "usual desk time", Modifier.weight(1f))
        StatCard(avg?.let { "$it min" } ?: "–", "avg wake → desk", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(value, fontSize = 20.sp, color = Palette.Sun, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, right: String, onRight: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        Text(
            right,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (onRight != null) Modifier.clickable(onClick = onRight).padding(4.dp) else Modifier,
        )
    }
}

private fun cellDescription(data: AppData, d: LocalDate, c: Insights.Cell, zone: ZoneId): String {
    val t = Insights.deskTime(data.logs[d.toString()], zone)?.format(shortTime)
    return d.format(dayFmt) + ": " + cellLabel(c) + (t?.let { ", desk at $it" } ?: "")
}

@Composable
private fun WeekGrid(data: AppData, now: LocalDateTime, zone: ZoneId, selected: LocalDate?, onTap: (LocalDate) -> Unit) {
    val today = now.toLocalDate()
    val weeks = Insights.weekGrid(today)
    val gap = 4.dp
    val letters = listOf("M", "", "W", "", "F", "", "S")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val label = 16.dp
        val cell = (maxWidth - label - gap * 15) / 16
        Column {
            // Month names above the first week of each month.
            Row(Modifier.height(16.dp).padding(start = label), horizontalArrangement = Arrangement.spacedBy(gap)) {
                weeks.forEachIndexed { i, w ->
                    val first = w.first()
                    val show = i == 0 || first.month != weeks[i - 1].first().month
                    Box(Modifier.width(cell)) {
                        if (show && i < 15) {
                            Text(
                                first.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            (0..6).forEach { row ->
                Row(Modifier.padding(bottom = gap), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    Text(letters[row], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(label - gap))
                    weeks.forEach { w ->
                        val d = w[row]
                        if (d.isAfter(today)) {
                            Spacer(Modifier.size(cell))
                        } else {
                            val c = Insights.cell(data, d, now, zone)
                            Box(
                                Modifier.size(cell)
                                    .cellSquare(c, selected = d == selected)
                                    .clickable { onTap(d) }
                                    .semantics { contentDescription = cellDescription(data, d, c, zone) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(data: AppData, year: Int, now: LocalDateTime, zone: ZoneId, selected: LocalDate?, onTap: (LocalDate) -> Unit) {
    val gap = 3.dp
    val today = now.toLocalDate()
    Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
        (1..12).forEach { m ->
            Text(
                java.time.Month.of(m).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    (1..31).forEach { day ->
        Row(Modifier.padding(top = gap), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (day == 1 || day % 5 == 1) "$day" else "",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp),
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                (1..12).forEach { m ->
                    val ym = YearMonth.of(year, m)
                    val mod = Modifier.weight(1f).height(15.dp)
                    if (day > ym.lengthOfMonth()) {
                        Spacer(mod)
                    } else {
                        val d = ym.atDay(day)
                        if (d.isAfter(today)) {
                            Box(mod.background(Color(0xFF161A23), RoundedCornerShape(3.dp)))
                        } else {
                            val c = Insights.cell(data, d, now, zone)
                            Box(
                                mod.cellSquare(c, 3.dp, d == selected)
                                    .clickable { onTap(d) }
                                    .semantics { contentDescription = cellDescription(data, d, c, zone) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, now: LocalDateTime, zone: ZoneId, selected: LocalDate?, onTap: (LocalDate) -> Unit) {
    val today = now.toLocalDate()
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val gap = 6.dp
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        java.time.DayOfWeek.entries.forEach {
            Text(
                it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.padding(top = gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            (0..6).forEach { i ->
                val d = week.getOrNull(i)
                val mod = Modifier.weight(1f).aspectRatio(1f)
                if (d == null) {
                    Spacer(mod)
                } else {
                    val c = if (d.isAfter(today)) Insights.Cell.Pending else Insights.cell(data, d, now, zone)
                    Box(
                        mod.cellSquare(c, 8.dp, d == selected)
                            .clickable(enabled = !d.isAfter(today)) { onTap(d) }
                            .semantics { contentDescription = cellDescription(data, d, c, zone) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${d.dayOfMonth}",
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                Insights.isGood(c) -> Palette.Ink
                                c == Insights.Cell.Away || c == Insights.Cell.LockOff || c == Insights.Cell.AfterEnd || c == Insights.Cell.Skipped -> Color.White
                                c == Insights.Cell.Pending -> Color(0xFF5A6275)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (Insights.isGood(c)) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        @Composable fun sq(c: Insights.Cell) = Box(Modifier.size(10.dp).cellSquare(c, 3.dp))
        @Composable fun t(s: String) = Text(s, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        t("Earlier"); sq(Insights.Cell.Early); sq(Insights.Cell.Mid); sq(Insights.Cell.Late); t("Later")
        Spacer(Modifier.width(4.dp)); sq(Insights.Cell.Away); t("Away")
        Spacer(Modifier.width(4.dp)); sq(Insights.Cell.LockOff); t("Lock off")
        Spacer(Modifier.width(4.dp)); sq(Insights.Cell.Missed); t("Missed")
    }
}

@Composable
private fun DayDetail(data: AppData, d: LocalDate, now: LocalDateTime, zone: ZoneId) {
    val log = data.logs[d.toString()]
    val c = Insights.cell(data, d, now, zone)
    val t = Insights.deskTime(log, zone)
    val mins = Insights.minutesToDesk(data.settings, log, zone)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(d.format(dayFmt), color = Palette.Sun, fontWeight = FontWeight.SemiBold)
        Text(
            if (t != null) "Desk at ${t.format(shortTime)}" + (mins?.let { " · $it min" } ?: "") +
                (if (c == Insights.Cell.AfterEnd || c == Insights.Cell.LockOff) " · ${cellLabel(c)}" else "")
            else cellLabel(c),
            style = MaterialTheme.typography.bodyMedium,
        )
        val n = log?.blockedAttempts ?: 0
        if (n > 0) Text("Reached for blocked apps $n×", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimeToDesk(data: AppData, today: LocalDate, zone: ZoneId) {
    val series = Insights.timeToDeskSeries(data, today, zone)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(14.dp)) {
        if (series.size < 2) {
            Text("Finish a few mornings to see your trend here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val values = series.map { it.second }
        val start = today.minusDays(29)
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val lo = values.min().toFloat()
            val hi = values.max().toFloat().coerceAtLeast(lo + 1f)
            val path = Path()
            series.forEachIndexed { i, (d, v) ->
                val x = size.width * (java.time.temporal.ChronoUnit.DAYS.between(start, d) / 29f)
                val y = size.height * (1f - (v - lo) / (hi - lo)) * 0.9f + size.height * 0.05f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Palette.Sun, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val tr = Insights.trend(values)
        Text(
            when {
                tr == null -> "Average ${Insights.average(values)} min from lock start to desk."
                tr.second < tr.first -> "Getting faster: ${tr.first} → ${tr.second} min"
                tr.second > tr.first -> "A bit slower: ${tr.first} → ${tr.second} min"
                else -> "Steady: about ${tr.second} min"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
