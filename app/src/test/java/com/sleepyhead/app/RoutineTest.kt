package com.sleepyhead.app

import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.DayLog
import com.sleepyhead.app.data.HomeLocation
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
    private val settings = Settings(wakeMinutes = 6 * 60 + 30, lockEndMinutes = 11 * 60)
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
        assertTrue(r1 is Routine.ProofResult.WrongOrder)
        val (d2, r2) = Routine.applyScan(d1, "CODE-A", at(7, 0), ZoneOffset.UTC)
        assertTrue(r2 is Routine.ProofResult.Completed && !r2.routineDone)
        val (d3, _) = Routine.applyScan(d2, "CODE-B", at(7, 20), ZoneOffset.UTC)
        assertTrue(Routine.isLockActive(d3, at(7, 30)))
        val (d4, r4) = Routine.applyScan(d3, "CODE-C", at(7, 45), ZoneOffset.UTC)
        assertTrue(r4 is Routine.ProofResult.Completed && r4.routineDone)
        assertFalse(Routine.isLockActive(d4, at(7, 46)))
        assertTrue(d4.logs["2026-11-16"]!!.onTime)
        assertEquals(1, Routine.streak(d4, LocalDate.of(2026, 11, 16)))
    }

    @Test fun finishJustBeforeLockEndIsOnTime() {
        var e = base
        listOf("CODE-A", "CODE-B", "CODE-C").forEach { e = Routine.applyScan(e, it, at(10, 59), ZoneOffset.UTC).first }
        assertTrue(e.logs["2026-11-16"]!!.onTime)
    }

    @Test fun nfcTagByIdOrByWrittenCode() {
        val paired = base.copy(steps = steps.map { if (it.id == "a") it.copy(nfcTagId = "04A1B2") else it })
        val (d1, r1) = Routine.applyTag(paired, "04a1b2", null, at(7, 0), ZoneOffset.UTC)
        assertTrue(r1 is Routine.ProofResult.Completed && r1.step.id == "a")
        // An unpaired tag that carries the step code also works.
        val (_, r2) = Routine.applyTag(d1, "ffff", "CODE-B", at(7, 5), ZoneOffset.UTC)
        assertTrue(r2 is Routine.ProofResult.Completed && r2.step.id == "b")
        assertEquals(Routine.ProofResult.Unknown, Routine.applyTag(d1, "ffff", null, at(7, 5)).second)
    }

    @Test fun travelModeKeepsLockOff() {
        val auto = base.copy(settings = settings.copy(autoTravel = true, home = HomeLocation(43.65, -79.38)))
        assertTrue(Routine.needsTravelCheck(auto, at(7, 0)))
        assertFalse(Routine.needsTravelCheck(auto, at(6, 0)))
        // Toronto home, phone in Montreal: away.
        assertTrue(Routine.isAway(auto.settings, 45.50, -73.57))
        // 500 m away from home: still home.
        assertFalse(Routine.isAway(auto.settings, 43.6545, -79.38))
        val away = auto.copy(logs = mapOf("2026-11-16" to DayLog(date = "2026-11-16", away = true)))
        assertFalse(Routine.isLockActive(away, at(7, 0)))
        assertFalse(Routine.needsTravelCheck(away, at(7, 0)))
        val home = auto.copy(logs = mapOf("2026-11-16" to DayLog(date = "2026-11-16", away = false)))
        assertTrue(Routine.isLockActive(home, at(7, 0)))
    }

    @Test fun travelDaysDoNotBreakStreak() {
        fun ok(date: String) = date to DayLog(date = date, completedAtMillis = 1, onTime = true)
        val d = base.copy(
            settings = settings.copy(autoTravel = true, home = HomeLocation(0.0, 0.0)),
            logs = mapOf(ok("2026-11-12"), "2026-11-13" to DayLog(date = "2026-11-13", away = true), ok("2026-11-16")),
        )
        assertEquals(2, Routine.streak(d, LocalDate.of(2026, 11, 16)))
    }

    @Test fun scanOutsideWindowIsOnlyATest() {
        val (d, r) = Routine.applyScan(base, "CODE-A", at(22, 0), ZoneOffset.UTC)
        assertTrue(r is Routine.ProofResult.Tested)
        assertEquals(d, base)
    }

    @Test fun nextLockStartSkipsWeekend() {
        // Friday 2026-11-20 at 10:00 -> Monday 2026-11-23 at 6:30.
        assertEquals(at(6, 30, LocalDate.of(2026, 11, 23)), Routine.nextLockStart(settings, at(10, 0, LocalDate.of(2026, 11, 20))))
        assertEquals(at(6, 30), Routine.nextLockStart(settings, at(5, 0)))
    }

    @Test fun testingAStep() {
        assertEquals(Routine.TestResult.Works, Routine.testStep(base, "a", Routine.stepForCode(base, "CODE-A")))
        assertEquals(Routine.TestResult.OtherStep(steps[1]), Routine.testStep(base, "a", Routine.stepForCode(base, "CODE-B")))
        assertEquals(Routine.TestResult.Unknown, Routine.testStep(base, "a", null))
    }

    @Test fun daysBeforeStartAreNotMissed() {
        val d = base.copy(settings = settings.copy(startedOn = "2026-11-16"))
        val now = at(12, 0, LocalDate.of(2026, 11, 17))
        assertEquals(Routine.DayStatus.DayOff, Routine.dayStatus(d, LocalDate.of(2026, 11, 13), now))
        assertEquals(Routine.DayStatus.Missed, Routine.dayStatus(d, LocalDate.of(2026, 11, 16), now))
    }

    @Test fun unknownCode() {
        assertEquals(Routine.ProofResult.Unknown, Routine.applyScan(base, "hello", at(7, 0)).second)
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

    private fun ms(t: LocalDateTime) = t.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun gapInLockMarksMorningAndBreaksStreak() {
        fun ok(date: String) = date to DayLog(date = date, completedAtMillis = 1, onTime = true)
        val monday = "2026-11-16" to DayLog("2026-11-16", completedAtMillis = ms(at(9, 0)), onTime = true)
        val d = base.copy(logs = mapOf(ok("2026-11-13"), monday))
        assertEquals(2, Routine.streak(d, LocalDate.of(2026, 11, 16)))
        // Service off from 7:00 to 8:00 on Monday, inside the window.
        val marked = Routine.markLockOff(d, ms(at(7, 0)), ms(at(8, 0)), ZoneOffset.UTC)
        assertTrue(marked.logs["2026-11-16"]!!.lockOff)
        assertEquals(0, Routine.streak(marked, LocalDate.of(2026, 11, 16)))
        assertEquals(Routine.DayStatus.LockOff, Routine.dayStatus(marked, LocalDate.of(2026, 11, 16), at(12, 0)))
    }

    @Test fun gapOutsideWindowOrAfterFinishIsIgnored() {
        // Overnight gap, Monday 20:00 to Tuesday 6:00: no window touched.
        assertEquals(base, Routine.markLockOff(base, ms(at(20, 0)), ms(at(6, 0, LocalDate.of(2026, 11, 17))), ZoneOffset.UTC))
        // Weekend gap: Saturday is not a routine day.
        val sat = LocalDate.of(2026, 11, 14)
        assertEquals(base, Routine.markLockOff(base, ms(at(7, 0, sat)), ms(at(9, 0, sat)), ZoneOffset.UTC))
        // Finished at 7:00, gap starts at 7:30: the morning still counts.
        val done = base.copy(logs = mapOf("2026-11-16" to DayLog("2026-11-16", completedAtMillis = ms(at(7, 0)), onTime = true)))
        val after = Routine.markLockOff(done, ms(at(7, 30)), ms(at(9, 0)), ZoneOffset.UTC)
        assertFalse(after.logs["2026-11-16"]!!.lockOff)
        assertEquals(1, Routine.streak(after, LocalDate.of(2026, 11, 16)))
    }

    @Test fun multiDayGapMarksEachMorning() {
        val marked = Routine.markLockOff(base, ms(at(12, 0)), ms(at(7, 0, LocalDate.of(2026, 11, 18))), ZoneOffset.UTC)
        assertFalse(marked.logs.containsKey("2026-11-16")) // gap began after Monday's window
        assertTrue(marked.logs["2026-11-17"]!!.lockOff)
        assertTrue(marked.logs["2026-11-18"]!!.lockOff)
    }
}
