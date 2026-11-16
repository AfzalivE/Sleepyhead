package com.sleepyhead.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.data.Step
import com.sleepyhead.app.data.Store
import com.sleepyhead.app.lock.LockService
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** Re-reads a value each time the screen resumes, for example after you return from system settings. */
@Composable
fun <T> rememberOnResume(read: () -> T): T {
    var value by remember { mutableStateOf(read()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) value = read() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return value
}

@Composable
fun SettingsScreen(store: Store, data: AppData, go: (Screen) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val locked = Routine.isLockActive(data, LocalDateTime.now())
    val lockOn = rememberOnResume { LockService.isEnabled(context) }
    var cameraOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraOk = it }
    val s = data.settings
    var editTime by remember { mutableStateOf<String?>(null) }

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        TopBar("Settings") { go(Screen.Home) }

        Section("Setup")
        SetupRow(
            "Morning lock (Accessibility)",
            if (lockOn) "On. Blocked apps send you back to your routine." else "Off. Sleepyhead cannot block apps yet.",
            done = lockOn,
            action = "Turn on",
        ) {
            context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        SetupRow("Camera", if (cameraOk) "Allowed. You can scan codes." else "Needed to scan your QR codes.", cameraOk, "Allow") {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
        SetupRow("Printed QR codes", "Print one code for each step and put it in that place.", null, "Open") { go(Screen.Codes) }
        if (!lockOn) {
            Text(
                "Tip: on Android 13 and newer, a sideloaded app can show \"Restricted setting\". " +
                    "Open App info for Sleepyhead, tap the ⋮ menu, choose \"Allow restricted settings\", then try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Open App info") }
        }

        Section("Morning")
        if (locked) {
            Text(
                "Your morning lock is on. You can change the routine after you finish it.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        TimeRow("Wake-up (lock starts)", s.wakeMinutes, !locked) { editTime = "wake" }
        TimeRow("At my desk by (on time)", s.targetMinutes, !locked) { editTime = "target" }
        TimeRow("Lock ends no matter what", s.lockEndMinutes, !locked) { editTime = "end" }

        Text("Days", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            DayOfWeek.entries.forEach { d ->
                val on = d.value in s.days
                FilterChip(
                    selected = on,
                    enabled = !locked,
                    onClick = { store.updateSettings { it.copy(days = if (on) it.days - d.value else it.days + d.value) } },
                    label = { Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                )
            }
        }

        Section("Routine")
        SetupRow("Steps", data.steps.joinToString(" → ") { it.title }, null, "Edit", enabled = !locked) { go(Screen.Steps) }
        SetupRow("Blocked apps", "${s.blockedPackages.size} apps blocked in the morning", null, "Choose", enabled = !locked) {
            go(Screen.Apps)
        }

        Section("Testing")
        Text(
            "To try it now, set the wake-up time to a few minutes ago, then open a blocked app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { store.resetToday() }, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Clear today's progress")
        }
        Spacer(Modifier.height(32.dp))
    }

    editTime?.let { which ->
        val initial = when (which) { "wake" -> s.wakeMinutes; "target" -> s.targetMinutes; else -> s.lockEndMinutes }
        TimeDialog(initial, onDismiss = { editTime = null }) { m ->
            store.updateSettings {
                when (which) {
                    "wake" -> it.copy(wakeMinutes = m)
                    "target" -> it.copy(targetMinutes = m)
                    else -> it.copy(lockEndMinutes = m)
                }
            }
            editTime = null
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SetupRow(
    title: String,
    subtitle: String,
    done: Boolean?,
    action: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (done == true) "✓ " else if (done == false) "• " else "") + title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (done == false) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (done != true) TextButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(minutesToText(minutes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun AppsScreen(store: Store, data: AppData, onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val apps = remember { InstalledApps.load(context) }
    var query by remember { mutableStateOf("") }
    val blocked = data.settings.blockedPackages
    val shown = apps
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .sortedByDescending { it.packageName in blocked }

    Column(modifier.padding(horizontal = 16.dp)) {
        TopBar("Blocked apps", onBack)
        Text(
            "These apps send you back to your routine during the morning lock.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyColumn {
            items(shown, key = { it.packageName }) { app ->
                val on = app.packageName in blocked
                val icon = remember(app.packageName) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        store.updateSettings {
                            it.copy(blockedPackages = if (on) it.blockedPackages - app.packageName else it.blockedPackages + app.packageName)
                        }
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                    Text(app.label, Modifier.weight(1f).padding(horizontal = 12.dp))
                    Checkbox(checked = on, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
fun StepsScreen(store: Store, data: AppData, onBack: () -> Unit, modifier: Modifier) {
    var confirmNewCodes by remember { mutableStateOf(false) }
    var deleting by remember { mutableIntStateOf(-1) }
    val steps = data.steps

    fun save(list: List<Step>) = store.setSteps(list)

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        TopBar("Steps", onBack)
        Text(
            "Do the steps in this order. Each step needs its own printed code in its place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        steps.forEachIndexed { i, step ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = step.title, label = { Text("Step ${i + 1}") }, singleLine = true,
                        onValueChange = { v -> save(steps.toMutableList().also { it[i] = step.copy(title = v) }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = step.place, label = { Text("Place (where the code goes)") }, singleLine = true,
                        onValueChange = { v -> save(steps.toMutableList().also { it[i] = step.copy(place = v) }) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton(enabled = i > 0, onClick = {
                            save(steps.toMutableList().also { java.util.Collections.swap(it, i, i - 1) })
                        }) { Icon(Icons.Outlined.ArrowUpward, "Move up") }
                        IconButton(enabled = i < steps.size - 1, onClick = {
                            save(steps.toMutableList().also { java.util.Collections.swap(it, i, i + 1) })
                        }) { Icon(Icons.Outlined.ArrowDownward, "Move down") }
                        IconButton(onClick = { deleting = i }) { Icon(Icons.Outlined.Delete, "Delete") }
                    }
                }
            }
        }
        Button(onClick = { save(steps + store.newStep("New step", "Somewhere")) }, modifier = Modifier.padding(vertical = 8.dp)) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(8.dp)); Text("Add step")
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(
            "If someone copied your codes, or you want a fresh set, make new codes. Old printed codes stop working.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { confirmNewCodes = true }, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Make new codes")
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmNewCodes) {
        AlertDialog(
            onDismissRequest = { confirmNewCodes = false },
            title = { Text("Make new codes?") },
            text = { Text("You must print the codes again. The old ones stop working.") },
            confirmButton = { TextButton(onClick = { store.regenerateCodes(); confirmNewCodes = false }) { Text("Make new codes") } },
            dismissButton = { TextButton(onClick = { confirmNewCodes = false }) { Text("Cancel") } },
        )
    }
    if (deleting in steps.indices) {
        AlertDialog(
            onDismissRequest = { deleting = -1 },
            title = { Text("Delete \"${steps[deleting].title}\"?") },
            confirmButton = {
                TextButton(onClick = { save(steps.filterIndexed { j, _ -> j != deleting }); deleting = -1 }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = -1 }) { Text("Cancel") } },
        )
    }
}
