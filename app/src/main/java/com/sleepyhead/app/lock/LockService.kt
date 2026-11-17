package com.sleepyhead.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.sleepyhead.app.SleepyheadApp
import com.sleepyhead.app.travel.TravelCheck
import com.sleepyhead.app.ui.MainActivity

/**
 * During the morning lock:
 * - when a blocked app opens, it sends you back to the calm Sleepyhead screen;
 * - when Sleepyhead's own App info, Storage or Accessibility page, or its uninstall dialog opens,
 *   it sends you back too, so the lock cannot be switched off on the way.
 * It reads screen text only in Settings and the package installer, and only during the lock.
 */
class LockService : AccessibilityService() {

    private var lastPkg: String? = null
    private var lastAt = 0L
    private var lastSeenWrite = 0L
    private var lastGuardCheck = 0L
    private val guard by lazy { SettingsGuard(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var recheckPending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        val store = (application as SleepyheadApp).store
        LockHealth.recordGap(this, store)
        LockHealth.markSeen(this)
        lastSeenWrite = SystemClock.elapsedRealtime()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSeenWrite > 60_000) {
            lastSeenWrite = now
            LockHealth.markSeen(this)
        }
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val store = (application as SleepyheadApp).store
        val data = store.data.value

        if (guard.isGuardedPackage(pkg)) {
            if (now - lastGuardCheck < 400) {
                // Too soon. Check once more when the page has settled, so the last change is not missed.
                if (!recheckPending) {
                    recheckPending = true
                    handler.postDelayed({ recheckPending = false; guardCheck(pkg) }, 450)
                }
                return
            }
            guardCheck(pkg)
            return
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pkg !in data.settings.blockedPackages) return
        // Decide home or away first. With a fresh location fix this finishes right away.
        TravelCheck.runIfNeeded(this, store)
        if (!store.isLockActive()) return

        val repeat = pkg == lastPkg && now - lastAt < 1500
        lastPkg = pkg
        lastAt = now
        if (!repeat) store.countBlockedAttempt()
        sendBack(pkg)
    }

    private fun guardCheck(pkg: String) {
        lastGuardCheck = SystemClock.elapsedRealtime()
        val store = (application as SleepyheadApp).store
        if (!store.isLockActive()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        if (guard.shouldBlock(pkg, root)) {
            // Back (not Home): after Home, Android delays activity starts for a few seconds,
            // so Sleepyhead would not open. Back closes the page or dialog, then Sleepyhead opens on top.
            performGlobalAction(GLOBAL_ACTION_BACK)
            sendBack(MainActivity.BLOCKED_SETTINGS)
        }
    }

    private fun sendBack(from: String) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_BLOCKED_PACKAGE, from)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    companion object {
        /** True while the service is connected in this process. */
        @Volatile
        var running = false
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, LockService::class.java)
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (s in splitter) {
                val cn = ComponentName.unflattenFromString(s)
                if (cn == me) return true
            }
            return false
        }
    }
}
