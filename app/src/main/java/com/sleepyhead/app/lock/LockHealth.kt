package com.sleepyhead.app.lock

import android.content.Context
import android.os.SystemClock
import com.sleepyhead.app.data.Store
import kotlin.math.max

/**
 * Keeps streaks honest. The lock service notes when it was last running. When it starts again
 * (or the app finds it is not running), the time in between is a gap. If a gap covers part of a
 * morning lock, that morning shows "Lock was off" and does not count for the streak.
 * Time while the phone was switched off does not count as a gap.
 */
object LockHealth {
    private const val PREFS = "lock_health"
    private const val KEY = "last_seen"
    /** Short gaps (a quick restart of the service) are ignored. */
    const val TOLERANCE_MS = 5 * 60_000L

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY, System.currentTimeMillis()).apply()
    }

    fun lastSeen(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)

    /** Records a gap from the last time the service was seen until now. */
    fun recordGap(context: Context, store: Store) {
        val last = lastSeen(context)
        if (last == 0L) return // The lock was never turned on: nothing to compare.
        val now = System.currentTimeMillis()
        val bootedAt = now - SystemClock.elapsedRealtime()
        val start = max(last, bootedAt)
        if (now - start < TOLERANCE_MS) return
        store.markLockOff(start, now)
    }
}
