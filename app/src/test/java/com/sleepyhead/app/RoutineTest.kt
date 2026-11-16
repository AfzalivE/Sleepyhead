package com.sleepyhead.app

import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.DayLog
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.data.Settings
import com.sleepyhead.app.data.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class RoutineTest {
    private val steps = listOf(
        Step("a", "Brush teeth", "Bathroom", "CODE-A"),
        Step("b", "Breakfast", "Kitchen", "CODE-B"),
        Step("c", "Desk", "Office", "CODE-C"),
    )
    private val settings = Settings(wakeMinutes = 6 * 60 + 30, targetMinutes = 8 * 60, lockEndMinutes = 11 * 60)
    private val base = AppData(settings = settings, steps = steps)

    // 2026-11-16 is a Monday.
    private fun at(h: Int, m: Int, date: LocalDate = LocalDate.of(2026, 11, 16)) = LocalDateTime.of(date, java.time.LocalTime.of(h, m))

    @Test fun lockOnlyInsideWindowOnRoutineDays() {
        assertFalse(Routine.isLockActive(base, at(6, 0)))
        assertTrue(Routine.isLockActive(base, at(6, 30)))
        assertTrue(Routine.isLockActive(base, at(10, 59)))
        assertFalse(Routine.isLockActive(base, at(11, 0)))
        // Saturday is not a routine day by default.
        assertFalse(Routine.isLockActive(base, at(7, 0, LocalDate.of(2026, 11, 21))))
    }

    @Test fun stepsMustBeScannedInOrder() {
        val (d1, r1) = Routine.applyScan(base, "CODE-B", at(7, 0), ZoneOffset.UTC)
        assertTrue(r1 is Routine.ScanResult.WrongOrder)
        val (d2, r2) = Routine.applyScan(d1, "CODE-A", at(7, 0), ZoneOffset.UTC)
        assertTrue(r2 is Routine.ScanResult.Completed && !r2.routineDone)
        val (d3, _) = Routine.applyScan(d2, "CODE-B", at(7, 20), ZoneOffset.UTC)
        assertTrue(Routine.isLockActive(d3, at(7, 30)))
        val (d4, r4) = Routine.applyScan(d3, "CODE-C", at(7, 45), ZoneOffset.UTC)
        assertTrue(r4 is Routine.ScanResult.Completed && r4.routineDone)
        assertFalse(Routine.isLockActive(d4, at(7, 46)))
        assertTrue(d4.logs["2026-11-16"]!!.onTime)
        assertEquals(1, Routine.streak(d4, LocalDate.of(2026, 11, 16)))
    }

    @Test fun lateFinishIsNotOnTime() {
        var d = base
        listOf("CODE-A", "CODE-B", "CODE-C").forEach { d = Routine.applyScan(d, it, at(8, 5), ZoneOffset.UTC).first }
        assertFalse(d.logs["2026-11-16"]!!.onTime)
    }

    @Test fun unknownCode() {
        assertEquals(Routine.ScanResult.Unknown, Routine.applyScan(base, "hello", at(7, 0)).second)
    }

    @Test fun streakSkipsWeekendsAndWaitsForToday() {
        fun ok(date: String) = date to DayLog(date = date, completedAtMillis = 1, onTime = true)
        val d = base.copy(
            logs = mapOf(
                ok("2026-11-11"), // Wed
                ok("2026-11-12"), // Thu
                ok("2026-11-13"), // Fri
                // Sat and Sun are not routine days.
            )
        )
        // Monday morning, not finished yet: streak still shows 3.
        assertEquals(3, Routine.streak(d, LocalDate.of(2026, 11, 16)))
        // Tuesday with Monday missing: streak is broken.
        assertEquals(0, Routine.streak(d, LocalDate.of(2026, 11, 17)))
        assertEquals(3, Routine.bestStreak(d))
    }

    @Test fun skipEndsLockAndBreaksStreak() {
        val d = base.copy(logs = mapOf("2026-11-16" to DayLog(date = "2026-11-16", skipped = true)))
        assertFalse(Routine.isLockActive(d, at(7, 0)))
        assertEquals(0, Routine.streak(d, LocalDate.of(2026, 11, 16)))
    }
}
