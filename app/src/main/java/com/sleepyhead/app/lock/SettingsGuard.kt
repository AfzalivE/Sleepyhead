package com.sleepyhead.app.lock

import android.annotation.SuppressLint
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.sleepyhead.app.R

/**
 * During the lock, finds the system screens that would switch Sleepyhead off:
 * its App info page (Force stop, Uninstall), its Storage page (Clear storage),
 * its Accessibility page (the "Use …" switch) and the uninstall dialog.
 * Other Settings screens, including the app list, stay open.
 * It matches several markers together, in the phone's own language where it can.
 */
class SettingsGuard(private val context: Context) {

    private val appLabel = context.getString(R.string.app_name)
    private val serviceLabel = context.getString(R.string.lock_service_label)

    private val forceStop = markers("force_stop", "Force stop")
    private val clearStorage = markers("clear_user_data_text", "Clear storage") + markers("clear_data_dlg_title", "Clear app data?")
    private val useSwitch = buildSet {
        add("Use $serviceLabel".lowercase())
        settingsString("accessibility_service_primary_switch_title")?.let { add(runCatching { it.format(serviceLabel) }.getOrDefault(it).lowercase()) }
        settingsString("accessibility_service_master_switch_title")?.let { add(runCatching { it.format(serviceLabel) }.getOrDefault(it).lowercase()) }
    }

    fun isGuardedPackage(pkg: String) =
        pkg == SETTINGS || pkg.endsWith("packageinstaller") || pkg.endsWith(".settings")

    /** True when [root] (a window of [pkg]) is one of the protected screens. */
    fun shouldBlock(pkg: String, root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        val texts = HashSet<String>()
        collect(root, texts, intArrayOf(0))
        val hasApp = appLabel.lowercase() in texts
        if (pkg.endsWith("packageinstaller")) return hasApp
        if (hasApp && texts.any { it in forceStop }) return true
        if (hasApp && texts.any { it in clearStorage }) return true
        if (texts.any { it in useSwitch }) return true
        return false
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableSet<String>, count: IntArray) {
        if (count[0]++ > 600) return
        node.text?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { out += it }
        node.contentDescription?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { out += it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, count)
        }
    }

    private fun markers(resName: String, fallback: String): Set<String> =
        setOfNotNull(fallback.lowercase(), settingsString(resName)?.lowercase())

    @SuppressLint("DiscouragedApi")
    private fun settingsString(name: String): String? = runCatching {
        val res = context.packageManager.getResourcesForApplication(SETTINGS)
        val id = res.getIdentifier(name, "string", SETTINGS)
        if (id == 0) null else res.getString(id)
    }.getOrNull()

    companion object {
        const val SETTINGS = "com.android.settings"
    }
}
