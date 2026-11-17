package com.sleepyhead.app.travel

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.sleepyhead.app.SleepyheadApp
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.data.Settings
import com.sleepyhead.app.data.Store
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides once per morning whether you are at home. If you are away and automatic travel mode is on,
 * the lock stays off for that day. When the location is not known, the lock stays on.
 */
object TravelCheck {

    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()

    /** Last known fixes younger than this are good enough. */
    private const val MAX_AGE_MS = 20 * 60 * 1000L

    fun hasForegroundPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Runs the home/away check if today still needs it. [onDone] runs after a decision, on any thread. */
    fun runIfNeeded(context: Context, store: Store, onDone: () -> Unit = {}) {
        if (!Routine.needsTravelCheck(store.data.value, LocalDateTime.now())) return
        if (!hasForegroundPermission(context)) return
        if (!running.compareAndSet(false, true)) return
        currentLocation(context) { loc ->
            running.set(false)
            if (loc != null && Routine.needsTravelCheck(store.data.value, LocalDateTime.now())) {
                store.setAwayToday(Routine.isAway(store.data.value.settings, loc.latitude, loc.longitude))
                onDone()
            }
        }
    }

    /** Gets a recent location: a fresh last-known fix if there is one, else a new fix. */
    @SuppressLint("MissingPermission")
    fun currentLocation(context: Context, callback: (Location?) -> Unit) {
        if (!hasForegroundPermission(context)) {
            callback(null); return
        }
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = lm.getProviders(true)
        val fresh = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_AGE_MS * 1_000_000 }
            .minByOrNull { it.accuracy }
        if (fresh != null) {
            callback(fresh); return
        }
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            callback(null); return
        }
        runCatching {
            LocationManagerCompat.getCurrentLocation(
                lm, provider, null as android.os.CancellationSignal?, executor,
                androidx.core.util.Consumer<Location?> { callback(it) },
            )
        }.onFailure { callback(null) }
    }

    /** Sets an alarm for the next lock start, so the check runs then even when the app is closed. */
    fun scheduleNext(context: Context, settings: Settings) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, LockStartReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!settings.autoTravel || settings.home == null || settings.days.isEmpty()) {
            am.cancel(pi); return
        }
        val now = LocalDateTime.now()
        var day = now.toLocalDate()
        repeat(8) {
            val at = day.atStartOfDay().plusMinutes(settings.wakeMinutes.toLong())
            if (Routine.isRoutineDay(settings, day) && at.isAfter(now)) {
                val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                return
            }
            day = day.plusDays(1)
        }
    }
}

/** Fires at lock start (and after a reboot) to run the travel check and set the next alarm. */
class LockStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SleepyheadApp
        TravelCheck.scheduleNext(context, app.store.data.value.settings)
        val pending = goAsync()
        // The fix can take a few seconds. Give up after 9 s so the receiver does not time out.
        val finished = AtomicBoolean(false)
        fun finish() { if (finished.compareAndSet(false, true)) pending.finish() }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 9_000)
        if (Routine.needsTravelCheck(app.store.data.value, LocalDateTime.now())) {
            TravelCheck.runIfNeeded(context, app.store) { finish() }
        } else {
            finish()
        }
    }
}
