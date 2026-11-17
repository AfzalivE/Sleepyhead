package com.sleepyhead.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure routine rules. No Android code here, so it is easy to unit test. */
object Routine {

    fun isRoutineDay(settings: Settings, date: LocalDate): Boolean =
        date.dayOfWeek.value in settings.days

    fun minutesOfDay(t: LocalDateTime): Int = t.hour * 60 + t.minute

    /** True while the morning window is open (lock start until lock end) on a routine day. */
    fun inMorningWindow(settings: Settings, now: LocalDateTime): Boolean {
        if (!isRoutineDay(settings, now.toLocalDate())) return false
        val m = minutesOfDay(now)
        return m >= settings.wakeMinutes && m < settings.lockEndMinutes
    }

    fun isFinished(log: DayLog?): Boolean =
        log != null && (log.completedAtMillis != null || log.skipped)

    /**
     * True when travel mode is on and the phone was away from home at lock start.
     * There is no manual switch on purpose: it would be an easy way out of the lock.
     */
    fun isTravelDay(data: AppData, date: LocalDate): Boolean =
        data.settings.autoTravel && data.logs[date.toString()]?.away == true

    /** True when distracting apps must be blocked now. */
    fun isLockActive(data: AppData, now: LocalDateTime): Boolean {
        if (data.steps.isEmpty()) return false
        if (!inMorningWindow(data.settings, now)) return false
        if (isTravelDay(data, now.toLocalDate())) return false
        return !isFinished(data.logs[now.toLocalDate().toString()])
    }

    /** True when today's home/away check still has to run. */
    fun needsTravelCheck(data: AppData, now: LocalDateTime): Boolean {
        val s = data.settings
        if (!s.autoTravel || s.home == null) return false
        if (!inMorningWindow(s, now)) return false
        val log = data.logs[now.toLocalDate().toString()]
        return !isFinished(log) && log?.away == null
    }

    fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun isAway(settings: Settings, lat: Double, lon: Double): Boolean {
        val home = settings.home ?: return false
        return distanceMeters(home.lat, home.lon, lat, lon) > settings.homeRadiusMeters
    }

    /** The next step to do today, or null when all steps are done. */
    fun nextStep(data: AppData, date: LocalDate): Step? {
        val doneIds = data.logs[date.toString()]?.done?.map { it.stepId }?.toSet() ?: emptySet()
        return data.steps.firstOrNull { it.id !in doneIds }
    }

    sealed interface ProofResult {
        data class Completed(val step: Step, val routineDone: Boolean) : ProofResult
        data class WrongOrder(val scanned: Step, val expected: Step) : ProofResult
        data class AlreadyDone(val step: Step) : ProofResult
        data object Unknown : ProofResult
        data object NothingLeft : ProofResult
    }

    fun stepForCode(data: AppData, code: String?): Step? =
        code?.trim()?.let { c -> data.steps.firstOrNull { it.code == c } }

    fun stepForTag(data: AppData, tagId: String?): Step? =
        tagId?.let { id -> data.steps.firstOrNull { it.nfcTagId.equals(id, ignoreCase = true) } }

    /** Marks [step] as done today if it is the next step. Returns the new data and what happened. */
    fun applyProof(
        data: AppData,
        step: Step?,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<AppData, ProofResult> {
        step ?: return data to ProofResult.Unknown
        val date = now.toLocalDate()
        val key = date.toString()
        val log = data.logs[key] ?: DayLog(date = key)
        if (log.done.any { it.stepId == step.id }) return data to ProofResult.AlreadyDone(step)
        val expected = nextStep(data, date) ?: return data to ProofResult.NothingLeft
        if (expected.id != step.id) return data to ProofResult.WrongOrder(step, expected)

        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val done = log.done + StepDone(step.id, nowMillis)
        val allDone = data.steps.all { s -> done.any { it.stepId == s.id } }
        val newLog = if (allDone) {
            log.copy(
                done = done,
                completedAtMillis = nowMillis,
                onTime = minutesOfDay(now) < data.settings.lockEndMinutes,
            )
        } else {
            log.copy(done = done)
        }
        return data.copy(logs = data.logs + (key to newLog)) to ProofResult.Completed(step, allDone)
    }

    fun applyScan(data: AppData, scanned: String, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()) =
        applyProof(data, stepForCode(data, scanned), now, zone)

    /** A tag counts when its ID is paired with a step, or when it carries a step's code. */
    fun applyTag(data: AppData, tagId: String?, tagCode: String?, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()) =
        applyProof(data, stepForTag(data, tagId) ?: stepForCode(data, tagCode), now, zone)

    private fun countsForStreak(data: AppData, day: LocalDate): Boolean? {
        val log = data.logs[day.toString()]
        if (log?.away == true && data.settings.autoTravel) return null // travel day: ignored
        if (!isRoutineDay(data.settings, day)) return null
        return log != null && log.onTime && !log.skipped
    }

    /**
     * Number of on-time mornings in a row, counting back from today.
     * Non-routine days and travel days are ignored. Today counts only when it is finished,
     * so an unfinished morning does not break the streak before it is over.
     */
    fun streak(data: AppData, today: LocalDate): Int {
        var count = 0
        var day = today
        if (!isFinished(data.logs[today.toString()])) day = day.minusDays(1)
        var guard = 0
        while (guard++ < 3660) {
            when (countsForStreak(data, day)) {
                true -> count++
                false -> return count
                null -> Unit
            }
            day = day.minusDays(1)
        }
        return count
    }

    /** Best streak ever, over the whole log. */
    fun bestStreak(data: AppData): Int {
        if (data.logs.isEmpty()) return 0
        var day = data.logs.keys.minOf { LocalDate.parse(it) }
        val last = data.logs.keys.maxOf { LocalDate.parse(it) }
        var best = 0
        var run = 0
        while (!day.isAfter(last)) {
            when (countsForStreak(data, day)) {
                true -> { run++; if (run > best) best = run }
                false -> run = 0
                null -> Unit
            }
            day = day.plusDays(1)
        }
        return best
    }
}
