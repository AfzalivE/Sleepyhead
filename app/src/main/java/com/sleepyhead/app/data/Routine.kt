package com.sleepyhead.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure routine rules. No Android code here, so it is easy to unit test. */
object Routine {

    fun isRoutineDay(settings: Settings, date: LocalDate): Boolean =
        date.dayOfWeek.value in settings.days

    fun minutesOfDay(t: LocalDateTime): Int = t.hour * 60 + t.minute

    /** True while the morning window is open (wake time until lock end) on a routine day. */
    fun inMorningWindow(settings: Settings, now: LocalDateTime): Boolean {
        if (!isRoutineDay(settings, now.toLocalDate())) return false
        val m = minutesOfDay(now)
        return m >= settings.wakeMinutes && m < settings.lockEndMinutes
    }

    fun isFinished(log: DayLog?): Boolean =
        log != null && (log.completedAtMillis != null || log.skipped)

    /** True when distracting apps must be blocked now. */
    fun isLockActive(data: AppData, now: LocalDateTime): Boolean {
        if (data.steps.isEmpty()) return false
        if (!inMorningWindow(data.settings, now)) return false
        return !isFinished(data.logs[now.toLocalDate().toString()])
    }

    /** The next step to do today, or null when all steps are done. */
    fun nextStep(data: AppData, date: LocalDate): Step? {
        val doneIds = data.logs[date.toString()]?.done?.map { it.stepId }?.toSet() ?: emptySet()
        return data.steps.firstOrNull { it.id !in doneIds }
    }

    sealed interface ScanResult {
        data class Completed(val step: Step, val routineDone: Boolean) : ScanResult
        data class WrongOrder(val scanned: Step, val expected: Step) : ScanResult
        data class AlreadyDone(val step: Step) : ScanResult
        data object Unknown : ScanResult
        data object NothingLeft : ScanResult
    }

    /** Applies a scanned QR text to today's log. Returns the new data and what happened. */
    fun applyScan(
        data: AppData,
        scanned: String,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<AppData, ScanResult> {
        val date = now.toLocalDate()
        val key = date.toString()
        val step = data.steps.firstOrNull { it.code == scanned.trim() }
            ?: return data to ScanResult.Unknown
        val log = data.logs[key] ?: DayLog(date = key)
        if (log.done.any { it.stepId == step.id }) return data to ScanResult.AlreadyDone(step)
        val expected = nextStep(data, date) ?: return data to ScanResult.NothingLeft
        if (expected.id != step.id) return data to ScanResult.WrongOrder(step, expected)

        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val done = log.done + StepDone(step.id, nowMillis)
        val allDone = data.steps.all { s -> done.any { it.stepId == s.id } }
        val newLog = if (allDone) {
            log.copy(
                done = done,
                completedAtMillis = nowMillis,
                onTime = minutesOfDay(now) <= data.settings.targetMinutes,
            )
        } else {
            log.copy(done = done)
        }
        return data.copy(logs = data.logs + (key to newLog)) to ScanResult.Completed(step, allDone)
    }

    /**
     * Number of on-time mornings in a row, counting back from today.
     * Days that are not routine days are ignored. Today counts only when it is finished,
     * so an unfinished morning does not break the streak before it is over.
     */
    fun streak(data: AppData, today: LocalDate): Int {
        var count = 0
        var day = today
        val todayLog = data.logs[today.toString()]
        if (!isFinished(todayLog)) day = day.minusDays(1)
        var guard = 0
        while (guard++ < 3660) {
            if (isRoutineDay(data.settings, day)) {
                val log = data.logs[day.toString()]
                if (log != null && log.onTime && !log.skipped) count++ else break
            }
            day = day.minusDays(1)
        }
        return count
    }

    /** Best streak ever, over the whole log. */
    fun bestStreak(data: AppData): Int {
        if (data.logs.isEmpty()) return 0
        val first = data.logs.keys.minOf { LocalDate.parse(it) }
        val last = data.logs.keys.maxOf { LocalDate.parse(it) }
        var best = 0
        var run = 0
        var day = first
        while (!day.isAfter(last)) {
            if (isRoutineDay(data.settings, day)) {
                val log = data.logs[day.toString()]
                if (log != null && log.onTime && !log.skipped) {
                    run++
                    if (run > best) best = run
                } else {
                    run = 0
                }
            }
            day = day.plusDays(1)
        }
        return best
    }

    fun formatMinutes(m: Int): String = "%d:%02d".format(m / 60, m % 60)
}
