package com.sleepyhead.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.Store
import com.sleepyhead.app.qr.QrCodes

private const val PAGES = 6

/** First-launch walk-through: schedule, apps, steps, permissions, travel, then codes. */
@Composable
fun OnboardingScreen(store: Store, data: AppData, modifier: Modifier) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    if (page > 0) BackHandler { page-- }

    Column(modifier.padding(horizontal = 16.dp)) {
        LinearProgressIndicator(
            progress = { (page + 1) / PAGES.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        )
        val body = Modifier.weight(1f).fillMaxWidth()
        when (page) {
            0 -> Page(body, "Good morning", scroll = true, brand = true) {
                Para("When you wake up, your phone pulls you into news, social media and work chat.")
                Para("Sleepyhead blocks those apps in the morning until you get up and finish your routine: " +
                    "for example brush your teeth, eat breakfast, and sit down at your desk.")
                Para("You prove each step by scanning a QR code or tapping an NFC tag that you put in that place. " +
                    "So you must get out of bed to unlock your phone.")
                Para("This setup takes about two minutes.")
            }
            1 -> Page(body, "When is your morning?", scroll = true) {
                Para("The lock starts when you wake up. It ends at the lock end time, even if you did not finish.")
                ScheduleSection(store, data, enabled = true)
            }
            2 -> Page(body, "Which apps distract you?", scroll = false) {
                AppsList(store, data, Modifier.weight(1f))
            }
            3 -> Page(body, "Your routine", scroll = true) {
                StepsEditor(store, data)
                Hint("You print the QR codes at the end of this setup. You can also pair NFC tags later in Settings.")
            }
            4 -> Page(body, "Permissions", scroll = true) {
                Para("Sleepyhead needs these to work. Nothing leaves your phone.")
                PermissionsSection()
            }
            5 -> Page(body, "Travel (optional)", scroll = true) {
                Para("When you are not at home, you cannot reach your codes or tags. " +
                    "Save your home location, and the lock stays off on mornings when you are away. " +
                    "You can skip this and set it up later in Settings.")
                TravelSection(store, data, enabled = true)
                Spacer(Modifier.height(16.dp))
                Para("Last step: print your QR codes and put each one in its place. " +
                    "Then test each one with the Test button in Settings → Steps.")
                OutlinedButton(onClick = { (context as? Activity)?.let { QrCodes.print(it, data.steps) } }) {
                    Icon(Icons.Outlined.Print, null); Spacer(Modifier.size(8.dp)); Text("Print or save as PDF")
                }
            }
        }
        // A line above the buttons shows where the scrolling page ends.
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (page > 0) TextButton(onClick = { page-- }) { Text("Back") } else Spacer(Modifier)
            if (page < PAGES - 1) {
                Button(onClick = { page++ }, enabled = page != 3 || data.steps.isNotEmpty()) { Text("Next") }
            } else {
                Button(onClick = { store.updateSettings { it.copy(onboarded = true) } }) { Text("Start") }
            }
        }
    }
}

@Composable
private fun Page(modifier: Modifier, title: String, scroll: Boolean, brand: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val m = if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier
    Column(m) {
        if (brand) {
            // The first page says clearly what this app is.
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SunMark(Modifier.size(120.dp))
                Text("Sleepyhead", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
        content()
    }
}

@Composable
private fun Para(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 24.sp,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}
