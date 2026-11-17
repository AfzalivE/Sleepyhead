package com.sleepyhead.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.outlined.Nfc
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.HomeLocation
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.data.Step
import com.sleepyhead.app.data.Store
import com.sleepyhead.app.lock.LockService
import com.sleepyhead.app.nfc.NfcHub
import com.sleepyhead.app.travel.TravelCheck
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
    val locked = Routine.isLockActive(data, LocalDateTime.now())
    val s = data.settings

    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        TopBar("Settings") { go(Screen.Home) }

        Section("Setup")
        PermissionsSection()
        SetupRow("Printed QR codes", "Print one code for each step and put it in that place.", null, "Open") { go(Screen.Codes) }

        Section("Morning lock")
        if (locked) {
            Hint("Your morning lock is on. You can change these settings after you finish your routine.", highlight = true)
        }
        ScheduleSection(store, data, enabled = !locked)

        Section("Routine")
        SetupRow(
            "Steps",
            data.steps.joinToString(" → ") { it.title + if (it.nfcTagId != null) " (NFC)" else "" },
            null, "Edit", enabled = !locked,
        ) { go(Screen.Steps) }
        SetupRow("Blocked apps", "${s.blockedPackages.size} apps blocked in the morning", null, "Choose", enabled = !locked) {
            go(Screen.Apps)
        }

        Section("Travel")
        TravelSection(store, data, enabled = !locked)

        Section("Testing")
        Hint("To try it now, set the lock start to a few minutes ago, then open a blocked app.")
        OutlinedButton(onClick = { store.resetToday() }, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Clear today's progress")
        }
        OutlinedButton(onClick = { store.updateSettings { it.copy(onboarded = false) } }) {
            Text("Show the welcome steps again")
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Accessibility, camera and NFC status, with buttons to fix each one. */
@Composable
fun PermissionsSection() {
    val context = LocalContext.current
    val lockOn = rememberOnResume { LockService.isEnabled(context) }
    val nfcOn = rememberOnResume { NfcHub.isEnabled(context) }
    var cameraOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraOk = it }

    SetupRow(
        "Morning lock (Accessibility)",
        if (lockOn) "On. Blocked apps send you back to your routine." else "Off. Sleepyhead cannot block apps yet.",
        done = lockOn,
        action = "Turn on",
    ) {
        context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    if (!lockOn) {
        Hint(
            "On Android 13 and newer, a sideloaded app can show \"Restricted setting\". " +
                "Open App info for Sleepyhead, tap the ⋮ menu, choose \"Allow restricted settings\", then try again."
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Text("Open App info") }
    }
    SetupRow("Camera", if (cameraOk) "Allowed. You can scan QR codes." else "Needed to scan QR codes.", cameraOk, "Allow") {
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }
    if (NfcHub.isAvailable(context)) {
        SetupRow("NFC", if (nfcOn) "On. You can use NFC tags." else "Off. Turn it on to use NFC tags.", nfcOn, "Turn on") {
            context.startActivity(Intent(AndroidSettings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Lock start, lock end and days. */
@Composable
fun ScheduleSection(store: Store, data: AppData, enabled: Boolean) {
    val s = data.settings
    var editTime by remember { mutableStateOf<String?>(null) }
    TimeRow("Lock starts (wake-up)", s.wakeMinutes, enabled) { editTime = "start" }
    TimeRow("Lock ends", s.lockEndMinutes, enabled) { editTime = "end" }
    if (s.lockEndMinutes <= s.wakeMinutes) {
        Hint("The lock must end after it starts, on the same day.", highlight = true)
    }
    Hint("Finish your routine before the lock ends to keep your streak.")

    Text("Days", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        DayOfWeek.entries.forEach { d ->
            val on = d.value in s.days
            FilterChip(
                selected = on,
                enabled = enabled,
                onClick = { store.updateSettings { it.copy(days = if (on) it.days - d.value else it.days + d.value) } },
                label = { Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
            )
        }
    }

    editTime?.let { which ->
        TimeDialog(if (which == "start") s.wakeMinutes else s.lockEndMinutes, onDismiss = { editTime = null }) { m ->
            store.updateSettings { if (which == "start") it.copy(wakeMinutes = m) else it.copy(lockEndMinutes = m) }
            editTime = null
        }
    }
}

/** Travel mode: home location and the home/away check at lock start. */
@Composable
fun TravelSection(store: Store, data: AppData, enabled: Boolean) {
    val context = LocalContext.current
    val s = data.settings
    var fgOk by remember { mutableStateOf(TravelCheck.hasForegroundPermission(context)) }
    val bgOk = rememberOnResume { TravelCheck.hasBackgroundPermission(context) }
    var bgNow by remember { mutableStateOf(bgOk) }
    var locating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        fgOk = TravelCheck.hasForegroundPermission(context)
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        bgNow = TravelCheck.hasBackgroundPermission(context)
    }
    val backgroundGranted = bgOk || bgNow

    // Turning travel mode off on a no-lock day starts the lock right away, and the
    // switch is disabled while the lock is on. So ask first.
    var confirmOff by remember { mutableStateOf(false) }
    val now = LocalDateTime.now()
    val noLockToday = Routine.isTravelDay(data, now.toLocalDate()) &&
        Routine.inMorningWindow(s, now) && !Routine.isFinished(data.logs[now.toLocalDate().toString()])
    fun setTravel(v: Boolean) {
        if (!v && noLockToday) { confirmOff = true; return }
        store.updateSettings { it.copy(autoTravel = v) }
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Start the lock now?") },
            text = { Text("The morning lock starts right away. You cannot change travel settings again until the lock ends at ${minutesToText(s.lockEndMinutes)}.") },
            confirmButton = {
                TextButton(onClick = {
                    store.updateSettings { it.copy(autoTravel = false) }
                    confirmOff = false
                }) { Text("Start the lock") }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } },
        )
    }

    SwitchRow(
        "Travel mode",
        "At lock start, Sleepyhead checks your location. If you are not at home, the lock stays off that day.",
        s.autoTravel, enabled && s.home != null && fgOk,
    ) { v -> setTravel(v) }

    if (!fgOk) {
        SetupRow("Location", "Needed to know if you are at home. Approximate location is enough.", false, "Allow") {
            fgLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    } else if (!backgroundGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        SetupRow(
            "Location all the time",
            "The check runs at lock start, when Sleepyhead is closed. Choose \"Allow all the time\".",
            false, "Allow",
        ) { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    }

    val home = s.home
    SetupRow(
        "Home",
        when {
            locating -> "Finding your location…"
            home != null -> "Saved. Anywhere within ${s.homeRadiusMeters / 1000} km counts as home."
            else -> "Not set. Set it while you are at home."
        },
        null,
        if (home == null) "Set to here" else "Update to here",
        enabled = enabled && fgOk && !locating,
    ) {
        locating = true
        status = null
        TravelCheck.currentLocation(context) { loc ->
            locating = false
            if (loc == null) {
                status = "Could not find your location. Check that location is on, then try again."
            } else {
                val first = store.data.value.settings.home == null
                // Saving home the first time also turns on the away check, since that is why you save it.
                store.updateSettings {
                    it.copy(home = HomeLocation(loc.latitude, loc.longitude), autoTravel = it.autoTravel || first)
                }
                status = if (first) "Home saved. The lock now stays off on mornings when you are away." else "Home updated."
            }
        }
    }
    status?.let { Hint(it, highlight = true) }
    if (s.autoTravel && !backgroundGranted) {
        Hint("Without \"all the time\" location, the check runs only when you open Sleepyhead or a blocked app.")
    }
}

@Composable
fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun Hint(text: String, highlight: Boolean = false) {
    Text(
        text,
        style = if (highlight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun SetupRow(
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
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles the switch, so tapping the label works too.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            minutesToText(minutes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
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
    Column(modifier.padding(horizontal = 16.dp)) {
        TopBar("Blocked apps", onBack)
        AppsList(store, data, Modifier.weight(1f))
    }
}

@Composable
fun AppsList(store: Store, data: AppData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = remember { InstalledApps.load(context) }
    var query by remember { mutableStateOf("") }
    val blocked = data.settings.blockedPackages
    // Sort once per search, so a row does not jump when you tick it.
    val shown = remember(query, apps) {
        apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .sortedByDescending { it.packageName in blocked }
    }
    Column(modifier) {
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
    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        TopBar("Steps", onBack)
        StepsEditor(store, data)
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Hint("If someone copied your codes, or you want a fresh set, make new QR codes. Old printed codes stop working. Paired NFC tags keep working.")
        OutlinedButton(onClick = { confirmNewCodes = true }, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Make new QR codes")
        }
        Spacer(Modifier.height(32.dp))
    }
    if (confirmNewCodes) {
        AlertDialog(
            onDismissRequest = { confirmNewCodes = false },
            title = { Text("Make new QR codes?") },
            text = { Text("You must print the codes again. The old ones stop working.") },
            confirmButton = { TextButton(onClick = { store.regenerateCodes(); confirmNewCodes = false }) { Text("Make new codes") } },
            dismissButton = { TextButton(onClick = { confirmNewCodes = false }) { Text("Cancel") } },
        )
    }
}

/** Edit, reorder, add and delete steps, and pair an NFC tag with each one. */
@Composable
fun StepsEditor(store: Store, data: AppData) {
    val context = LocalContext.current
    var deleting by remember { mutableIntStateOf(-1) }
    val pairing by NfcHub.pairing.collectAsStateWithLifecycle()
    val steps = data.steps
    val hasNfc = remember { NfcHub.isAvailable(context) }

    fun save(list: List<Step>) = store.setSteps(list)

    Hint(
        "Do the steps in this order. You prove each step with its printed QR code" +
            (if (hasNfc) " or an NFC tag that you put in that place." else ".")
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
                    value = step.place, label = { Text("Place (where the code or tag goes)") }, singleLine = true,
                    onValueChange = { v -> save(steps.toMutableList().also { it[i] = step.copy(place = v) }) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (hasNfc) {
                        if (step.nfcTagId == null) {
                            TextButton(onClick = { NfcHub.startPairing(step.id) }) {
                                Icon(Icons.Outlined.Nfc, null); Spacer(Modifier.size(6.dp)); Text("Pair NFC tag")
                            }
                        } else {
                            TextButton(onClick = { store.unpairTag(step.id) }) {
                                Icon(Icons.Outlined.Nfc, null); Spacer(Modifier.size(6.dp)); Text("Remove tag")
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
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

    pairing?.let { id ->
        val step = steps.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { NfcHub.cancelPairing() },
            icon = { Icon(Icons.Outlined.Nfc, null) },
            title = { Text("Hold your phone to the tag") },
            text = {
                Text(
                    "Touch the back of your phone to the NFC tag for \"${step?.title}\". " +
                        (if (!NfcHub.isEnabled(context)) "NFC is off. Turn it on in system settings first. " else "") +
                        "Then put the tag in ${step?.place}."
                )
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { NfcHub.cancelPairing() }) { Text("Cancel") } },
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
