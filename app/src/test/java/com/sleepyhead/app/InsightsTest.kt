package com.sleepyhead.app

import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.DayLog
import com.sleepyhead.app.data.Insights
import com.sleepyhead.app.data.Settings
import com.sleepyhead.app.data.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class InsightsTest {
    private val zone = ZoneOffset.UTC
    // Lock 6:00 to 9:00, so thirds end at 7:00 and 8:00.
    private val settings = Settings(wakeMinutes = 6 * 60, lockEndMinutes = 9 * 60, startedOn = "2026-01-01")
    private val base = AppData(settings = settings, steps = listOf(Step("a", "A", "P", "C")))
    private val monday = LocalDate.of(2026, 11, 16)
    private val now = LocalDateTime.of(monday, LocalTime.of(12, 0))

    private fun done(d: LocalDate, h: Int, m: Int, onTime: Boolean = true) = d.toString() to DayLog(
        d.toString(), completedAtMillis = LocalDateTime.of(d, LocalTime.of(h, m)).toInstant(zone).toEpochMilli(), onTime = onTime,
    )

    @Test fun cellBrightnessFollowsTimeOfDay() {
        val wide = base.copy(settings = settings.copy(wakeMinutes = 2 * 60, lockEndMinutes = 23 * 60 + 59))
        val fri = monday.minusDays(3)
        val thu = monday.minusDays(4)
        val wed = monday.minusDays(5)
        val d = wide.copy(logs = mapOf(done(monday, 7, 40), done(fri, 9, 30), done(thu, 10, 30), done(wed, 9, 0, onTime = false)))
        assertEquals(Insights.Cell.Early, Insights.cell(d, monday, now, zone))
        assertEquals(Insights.Cell.Mid, Insights.cell(d, fri, now, zone))
        assertEquals(Insights.Cell.Late, Insights.cell(d, thu, now, zone))
        assertEquals(Insights.Cell.AfterEnd, Insights.cell(d, wed, now, zone))
        assertEquals(Insights.Cell.Off, Insights.cell(d, monday.minusDays(1), now, zone)) // Sunday
        assertEquals(Insights.Cell.Missed, Insights.cell(d, monday.minusDays(6), now, zone)) // Tuesday, nothing
    }

    @Test fun deskTimeStatsAndTrend() {
        val d = base.copy(logs = mapOf(done(monday, 7, 0), done(monday.minusDays(3), 7, 30), done(monday.minusDays(4), 8, 0)))
        assertEquals(LocalTime.of(7, 30), Insights.usualDeskTime(d, monday, zone))
        assertEquals(LocalTime.of(7, 0), Insights.earliestDeskTime(d, monday, zone))
        assertEquals(listOf(480, 450, 420), Insights.deskTimeSeries(d, monday, zone).map { it.second })
        assertNull(Insights.trend(listOf(480, 450, 420)))
        assertEquals(470 to 430, Insights.trend(listOf(480, 460, 440, 420)))
    }

    @Test fun milestones() {
        assertEquals(14, Insights.nextMilestone(12))
        assertEquals(10, Insights.previousMilestone(12))
        assertEquals(3, Insights.nextMilestone(0))
        assertEquals(150, Insights.nextMilestone(120))
        assertEquals("two weeks", Insights.milestoneName(14))
    }

    @Test fun bestRunAndWeekGrid() {
        val d = base.copy(logs = mapOf(done(monday, 7, 0), done(monday.minusDays(3), 7, 0), done(monday.minusDays(4), 7, 0)))
        val run = Insights.bestRun(d)!!
        assertEquals(3, run.length)
        assertEquals(monday, run.end)
        val grid = Insights.weekGrid(monday)
        assertEquals(16, grid.size)
        assertEquals(monday, grid.last().first())
        assertEquals(java.time.DayOfWeek.MONDAY, grid.first().first().dayOfWeek)
    }

    @Test fun headlineOnlyOnExactMilestones() {
        val e = com.sleepyhead.app.ui.Encouragement
        assertEquals("Ten in a row. You're on fire.", e.doneHeadline(10))
        assertEquals("11 in a row. Good morning!", e.doneHeadline(11))
        assertEquals("You're at your desk.", e.doneHeadline(1))
    }

    @Test fun lockGoalNamesWhatTodayWins() {
        val e = com.sleepyhead.app.ui.Encouragement
        assertEquals("Finish by 9:00 to start a new streak.", e.lockGoal(0, 5, "9:00"))
        assertEquals("Finish by 9:00 to reach a full week.", e.lockGoal(6, 10, "9:00"))
        assertEquals("Finish by 9:00 for a new best streak.", e.lockGoal(11, 11, "9:00"))
        assertEquals("Finish by 9:00 to make it 12.", e.lockGoal(11, 20, "9:00"))
        assertEquals("Finish by 9:00 to reach 3 in a row.", e.lockGoal(2, 2, "9:00"))
        assertEquals("", e.skipCost(0))
        assertEquals("Skipping ends your 4-morning streak.", e.skipCost(4))
    }
}
