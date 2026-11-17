package com.sleepyhead.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Pure numbers and colours-by-meaning for the History and Home screens. Unit tested. */
object Insights {

    /** What one square in the grids means. Early, Mid and Late are on-time finishes. */
    enum class Cell { Early, Mid, Late, AfterEnd, Away, LockOff, Skipped, Missed, Off, Pending }

    fun deskTime(log: DayLog?, zone: ZoneId): LocalTime? =
        log?.completedAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }

    /** The time of day you finished, as minutes after midnight. */
    fun deskMinutes(log: DayLog?, zone: ZoneId): Int? = deskTime(log, zone)?.let { it.hour * 60 + it.minute }

    /** Square brightness by time of day: before 8:00 is early, 8:00 to 10:00 is mid, later is late. */
    const val EARLY_BEFORE = 8 * 60
    const val MID_BEFORE = 10 * 60

    fun minutesToTime(m: Int): LocalTime = LocalTime.of((m / 60).coerceIn(0, 23), m % 60)

    fun cell(data: AppData, date: LocalDate, now: LocalDateTime, zone: ZoneId): Cell =
        when (Routine.dayStatus(data, date, now)) {
            Routine.DayStatus.OnTime -> {
                val m = deskMinutes(data.logs[date.toString()], zone) ?: 0
                when {
                    m < EARLY_BEFORE -> Cell.Early
                    m < MID_BEFORE -> Cell.Mid
                    else -> Cell.Late
                }
            }
            Routine.DayStatus.Late -> Cell.AfterEnd
            Routine.DayStatus.Away -> Cell.Away
            Routine.DayStatus.LockOff -> Cell.LockOff
            Routine.DayStatus.Skipped -> Cell.Skipped
            Routine.DayStatus.Missed -> Cell.Missed
            Routine.DayStatus.DayOff -> Cell.Off
            Routine.DayStatus.Pending -> Cell.Pending
        }

    fun isGood(c: Cell) = c == Cell.Early || c == Cell.Mid || c == Cell.Late

    /** Days that could have counted: routine days that are over, not travel days, not before first use. */
    private fun countedDays(data: AppData, days: List<LocalDate>, now: LocalDateTime, zone: ZoneId) =
        days.map { it to cell(data, it, now, zone) }
            .filter { (_, c) -> c != Cell.Off && c != Cell.Pending && c != Cell.Away }

    /** Percent of counted days in the last [n] days that were good, or null when there are none. */
    fun onTimePercent(data: AppData, now: LocalDateTime, zone: ZoneId, n: Int = 30): Int? {
        val days = (0 until n).map { now.toLocalDate().minusDays(it.toLong()) }
        val counted = countedDays(data, days, now, zone)
        if (counted.isEmpty()) return null
        return Math.round(100f * counted.count { isGood(it.second) } / counted.size)
    }

    /** Median time of day you finished, over the last [n] days. */
    fun usualDeskTime(data: AppData, today: LocalDate, zone: ZoneId, n: Int = 30): LocalTime? {
        val mins = deskTimeSeries(data, today, zone, n).map { it.second }.sorted()
        if (mins.isEmpty()) return null
        return minutesToTime(mins[mins.size / 2])
    }

    /** Time of day you finished (minutes after midnight) for each finished day in the last [n] days, oldest first. */
    fun deskTimeSeries(data: AppData, today: LocalDate, zone: ZoneId, n: Int = 30): List<Pair<LocalDate, Int>> =
        (n - 1 downTo 0).mapNotNull { back ->
            val d = today.minusDays(back.toLong())
            deskMinutes(data.logs[d.toString()], zone)?.let { d to it }
        }

    /** Earliest time of day you finished in the last [n] days. */
    fun earliestDeskTime(data: AppData, today: LocalDate, zone: ZoneId, n: Int = 30): LocalTime? =
        deskTimeSeries(data, today, zone, n).minOfOrNull { it.second }?.let { minutesToTime(it) }

    fun average(values: List<Int>): Int? = if (values.isEmpty()) null else Math.round(values.average()).toInt()

    /** Average of the first and the last half of the series, to show a trend. Null when it is too short. */
    fun trend(series: List<Int>): Pair<Int, Int>? {
        if (series.size < 4) return null
        val half = series.size / 2
        return average(series.take(half))!! to average(series.takeLast(half))!!
    }

    private val milestones = listOf(3, 5, 7, 10, 14, 21, 30, 50, 100)

    fun nextMilestone(streak: Int): Int = milestones.firstOrNull { it > streak } ?: ((streak / 50) + 1) * 50

    fun previousMilestone(streak: Int): Int = milestones.lastOrNull { it <= streak }
        ?: if (streak >= 100) (streak / 50) * 50 else 0

    fun milestoneName(n: Int): String = when (n) {
        7 -> "a full week"
        14 -> "two weeks"
        21 -> "three weeks"
        30 -> "a whole month"
        else -> "$n in a row"
    }

    data class Run(val length: Int, val end: LocalDate)

    /** The longest streak and the day it ended (the most recent one when two are equal). */
    fun bestRun(data: AppData): Run? {
        if (data.logs.isEmpty()) return null
        var day = data.logs.keys.minOf { LocalDate.parse(it) }
        val last = data.logs.keys.maxOf { LocalDate.parse(it) }
        var best: Run? = null
        var run = 0
        while (!day.isAfter(last)) {
            when (Routine.countsForStreak(data, day)) {
                true -> { run++; if (run >= (best?.length ?: 1)) best = Run(run, day) }
                false -> run = 0
                null -> Unit
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** The 16-week grid: 16 columns of Monday..Sunday, the last column holds today. */
    fun weekGrid(today: LocalDate, weeks: Int = 16): List<List<LocalDate>> {
        val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (weeks - 1 downTo 0).map { w ->
            val monday = lastMonday.minusWeeks(w.toLong())
            (0..6).map { monday.plusDays(it.toLong()) }
        }
    }

    data class YearSummary(val good: Int, val away: Int)

    fun yearSummary(data: AppData, year: Int, now: LocalDateTime, zone: ZoneId): YearSummary {
        var good = 0
        var away = 0
        var d = LocalDate.of(year, 1, 1)
        val end = minOf(LocalDate.of(year, 12, 31), now.toLocalDate())
        while (!d.isAfter(end)) {
            when (cell(data, d, now, zone)) {
                Cell.Early, Cell.Mid, Cell.Late -> good++
                Cell.Away -> away++
                else -> Unit
            }
            d = d.plusDays(1)
        }
        return YearSummary(good, away)
    }
}
