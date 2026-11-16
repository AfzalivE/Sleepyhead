package com.sleepyhead.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable?)

object InstalledApps {

    /** Common news, social and work chat apps. Installed ones are blocked by default. */
    val SUGGESTED = listOf(
        // Social
        "com.twitter.android", "com.instagram.android", "com.facebook.katana", "com.reddit.frontpage",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.linkedin.android", "com.pinterest",
        "com.instagram.barcelona", "com.bluesky.app", "xyz.blueskyweb.app", "org.joinmastodon.android",
        "com.google.android.youtube", "com.tumblr",
        // News
        "com.google.android.apps.magazines", "flipboard.app", "com.nytimes.android", "bbc.mobile.news.ww",
        "com.cnn.mobile.android.phone", "com.guardian", "com.medium.reader", "com.ycombinator.news",
        "ca.cbc.mobile.android.cbcnews", "com.microsoft.amp.apps.bingnews",
        // Work chat and mail
        "com.Slack", "com.microsoft.teams", "com.google.android.apps.dynamite", "com.discord",
        "com.microsoft.office.outlook", "com.google.android.gm",
    )

    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString(), runCatching { it.loadIcon(pm) }.getOrNull()) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
