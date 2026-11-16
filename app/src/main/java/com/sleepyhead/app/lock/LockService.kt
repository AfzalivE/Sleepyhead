package com.sleepyhead.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.sleepyhead.app.SleepyheadApp
import com.sleepyhead.app.ui.MainActivity

/**
 * Watches which app comes to the front. During the morning lock, when a blocked app opens,
 * it sends you back to the calm Sleepyhead screen instead.
 * It reads only the package name of the app in front. It reads no screen content.
 */
class LockService : AccessibilityService() {

    private var lastPkg: String? = null
    private var lastAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val store = (application as SleepyheadApp).store
        val data = store.data.value
        if (pkg !in data.settings.blockedPackages) return
        if (!store.isLockActive()) return

        val now = SystemClock.elapsedRealtime()
        val repeat = pkg == lastPkg && now - lastAt < 1500
        lastPkg = pkg
        lastAt = now
        if (!repeat) store.countBlockedAttempt()

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_BLOCKED_PACKAGE, pkg)
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    companion object {
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
