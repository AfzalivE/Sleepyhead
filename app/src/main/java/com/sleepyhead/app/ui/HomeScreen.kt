package com.sleepyhead.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sleepyhead.app.data.Insights
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sleepyhead.app.data.AppData
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.data.Store
import com.sleepyhead.app.lock.LockService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TopInk = Color(0xFFB4BCCB)

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun minutesToText(m: Int): String = LocalTime.of(m / 60, m % 60).format(timeFmt)

@Composable
fun HomeScreen(
    store: Store,
    data: AppData,
    blockedFrom: String?,
    onBlockedSeen: () -> Unit,
    go: (Screen) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    // Tick once a second so the timer and the lock state stay fresh.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now(); delay(1000)
        }
    }
    val today = now.toLocalDate()
    val log = data.logs[today.toString()]
    val lockActive = Routine.isLockActive(data, now)
    val next = Routine.nextStep(data, today)
    val streak = Routine.streak(data, today)
    var message by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val doneCount = log?.done?.size ?: 0
    val finished = log?.completedAtMillis != null
    // Celebrate when progress grows while this screen is open (QR or NFC).
    var lastDone by remember { mutableIntStateOf(doneCount) }
    var lastFinished by remember { mutableStateOf(finished) }
    var confettiKey by remember { mutableIntStateOf(0) }
    var justFinished by remember { mutableStateOf(false) }
    LaunchedEffect(doneCount, finished) {
        if (doneCount > lastDone) celebrateHaptic(view)
        if (finished && !lastFinished) {
            confettiKey++
            justFinished = true
        }
        lastDone = doneCount
        lastFinished = finished
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        message = proofMessage(store.scan(text))
    }
    fun scan() {
        val opts = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(next?.let { "Scan the code in: ${it.place}" } ?: "Scan a Sleepyhead code")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanner.launch(opts)
    }

    Box(modifier) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Top bar. Small and quiet on purpose. Always light: it sits on the dark top of the sky.
        CompositionLocalProvider(LocalContentColor provides TopInk) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (streak > 0) "$streak-morning streak" else "Sleepyhead",
                style = MaterialTheme.typography.titleSmall,
                color = TopInk,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { go(Screen.History) }) { Icon(Icons.Outlined.CalendarMonth, "History") }
            IconButton(onClick = { go(Screen.Codes) }) { Icon(Icons.Outlined.QrCode2, "QR codes") }
            IconButton(onClick = { go(Screen.Settings) }) { Icon(Icons.Outlined.Settings, "Settings") }
        }
        }

        if (blockedFrom == MainActivity.BLOCKED_SETTINGS && lockActive) {
            Notice("Sleepyhead's settings are locked until you finish your morning.", onDismiss = onBlockedSeen)
        } else if (blockedFrom != null && lockActive) {
            val label = remember(blockedFrom) { InstalledApps.label(context, blockedFrom) }
            Notice("$label can wait. Finish your morning first.", onDismiss = onBlockedSeen)
        }
        val serviceOn = LockService.isEnabled(context)
        if (inWindowNow(data, now) && !serviceOn && !Routine.isFinished(log) && !Routine.isTravelDay(data, today)) {
            LockOffCard()
        }
        message?.let { Notice(it, onDismiss = { message = null }) }

        Spacer(Modifier.weight(1f))

        val travel = Routine.isTravelDay(data, today)
        val finishedAt = log?.completedAtMillis
        val inWindow = Routine.inMorningWindow(data.settings, now)
        when {
            !inWindow -> RestView(data, now, streak) { go(Screen.Steps) }
            finishedAt != null -> DoneView(data, now, finishedAt, log.onTime, log.lockOff, streak, Routine.bestStreak(data), justFinished)
            travel -> {
                Text("✈", fontSize = 56.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Big("No lock today")
                Small("You were away from home at lock start. Your streak is safe.")
                if (next != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { scan() }) { Text("Do the routine anyway") }
                }
            }
            log?.skipped == true -> {
                Big("Skipped today")
                Small("Your apps are unlocked. Tomorrow is a new morning.")
                TextButton(onClick = { store.undoSkipToday() }) { Text("Undo skip") }
            }
            next == null -> Big("Add a step in Settings to start.")
            else -> NextStepView(data, now, next.title, next.place, next.nfcTagId != null, lockActive) { scan() }
        }

        Spacer(Modifier.weight(1f))

        if (inWindow && finishedAt == null && log?.skipped != true) {
            StepTrail(data, today)
            Spacer(Modifier.height(16.dp))
        }
        if (lockActive && serviceOn) {
            HoldToSkip { store.skipToday() }
        } else if (!serviceOn && !inWindow) {
            Small("The morning lock is off. Turn it on in Settings.")
        }
    }
    if (confettiKey > 0) Confetti(confettiKey)
    }
}

@Composable
private fun NextStepView(
    data: AppData,
    now: LocalDateTime,
    title: String,
    place: String,
    hasTag: Boolean,
    lockActive: Boolean,
    onScan: () -> Unit,
) {
    val s = data.settings
    val nowMin = Routine.minutesOfDay(now)
    val inWindow = Routine.inMorningWindow(s, now)
    val doneCount = data.logs[now.toLocalDate().toString()]?.done?.size ?: 0
    Text(
        "STEP ${(doneCount + 1).coerceAtMost(data.steps.size)} OF ${data.steps.size}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        letterSpacing = 4.sp,
    )
    Spacer(Modifier.height(8.dp))
    // The new step slides up into place when the last one is done.
    AnimatedContent(
        targetState = title to place,
        transitionSpec = {
            (slideInVertically(tween(450)) { it / 2 } + fadeIn(tween(450))) togetherWith
                (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(300)))
        },
        label = "next-step",
    ) { (t, p) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Big(t)
            Small(p)
        }
    }
    Spacer(Modifier.height(32.dp))
    if (inWindow) {
        val secondsSinceWake = (nowMin - s.wakeMinutes) * 60 + now.second
        Text(
            formatDuration(secondsSinceWake),
            fontSize = 56.sp,
            fontWeight = FontWeight.ExtraLight,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val left = s.lockEndMinutes - nowMin
        Small("since ${minutesToText(s.wakeMinutes)} · lock ends ${minutesToText(s.lockEndMinutes)} ($left min)")
    } else if (Routine.isRoutineDay(s, now.toLocalDate()) && nowMin < s.wakeMinutes) {
        Small("Your morning starts at ${minutesToText(s.wakeMinutes)}. Sleep well.")
    } else {
        Small("No morning lock right now. You can still practice the routine.")
    }
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onScan,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = if (lockActive) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
    ) {
        Icon(Icons.Outlined.QrCodeScanner, null)
        Spacer(Modifier.size(12.dp))
        Text(if (hasTag) "I'm here · Scan or tap" else "I'm here · Scan the code", fontSize = 18.sp)
    }
    if (hasTag) {
        Small("Or tap your phone on the NFC tag in $place.")
    }
}

@Composable
private fun DoneView(
    data: AppData,
    now: LocalDateTime,
    finishedAt: Long,
    onTime: Boolean,
    lockOff: Boolean,
    streak: Int,
    best: Int,
    animate: Boolean,
) {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(finishedAt).atZone(zone).toLocalTime().format(timeFmt)
    val mins = Insights.minutesToDesk(data.settings, data.logs[now.toLocalDate().toString()], zone)
    // The sun is in the sky behind. Leave room for it.
    Spacer(Modifier.height(150.dp))
    Big(if (onTime && !lockOff) Encouragement.doneHeadline(streak) else "You're at your desk.")
    Small(
        when {
            lockOff -> "Done at $t. The lock was off for part of this morning, so it does not count for your streak."
            onTime -> "At your desk at $t" + (mins?.let { " · $it min after waking" } ?: "")
            else -> "Done at $t, after the lock ended. You still did it."
        }
    )
    if (streak > 0) {
        Spacer(Modifier.height(20.dp))
        if (animate) CountUp(streak) else Text("$streak", fontSize = 56.sp, fontFamily = Palette.Serif)
        Small(if (streak == 1) "good morning in a row" else "good mornings in a row")
        val line = Encouragement.streakLine(streak, best)
        if (line.isNotEmpty()) {
            Text(
                line,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    MiniWeek(data, now, animateToday = animate, letters = true, onLight = true)
    Spacer(Modifier.height(16.dp))
    Text("Your phone is all yours ☀", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
}

/** The last 7 days as squares. On the done screen, today's square pops in. */
@Composable
private fun MiniWeek(data: AppData, now: LocalDateTime, animateToday: Boolean, letters: Boolean = false, onLight: Boolean = false) {
    val zone = ZoneId.systemDefault()
    val pop = remember { Animatable(if (animateToday) 0f else 1f) }
    LaunchedEffect(Unit) {
        delay(900)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (6 downTo 0).forEach { back ->
            val date = now.toLocalDate().minusDays(back.toLong())
            val c = Insights.cell(data, date, now, zone)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val m = if (back == 0) Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value } else Modifier
                // On the bright sunrise, a day off is a soft shadow, not a dark hole.
                val sq = when {
                    onLight && c == Insights.Cell.Off -> m.size(22.dp).background(Palette.Ink.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    // On the bright sky, a thin dark edge keeps the light squares visible.
                    onLight -> m.size(22.dp).cellSquare(c, 6.dp).border(if (back == 0) 2.dp else 1.dp, Palette.Ink.copy(alpha = if (back == 0) 0.7f else 0.25f), RoundedCornerShape(6.dp))
                    else -> m.size(22.dp).cellSquare(c, 6.dp, selected = back == 0 && Insights.isGood(c))
                }
                Box(
                    sq
                        .semantics { contentDescription = date.dayOfWeek.toString() + ": " + cellLabel(c) },
                )
                if (letters) {
                    Text(
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Your steps as chips: done ones show the time, the current one has a sun outline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTrail(data: AppData, date: java.time.LocalDate) {
    val done = data.logs[date.toString()]?.done?.associate { it.stepId to it.atMillis } ?: emptyMap()
    val current = data.steps.firstOrNull { it.id !in done }?.id
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.steps.forEach { step ->
            val at = done[step.id]
            val shape = RoundedCornerShape(14.dp)
            val bg by animateColorAsState(
                if (at != null) Palette.Sun.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), tween(400), label = "chip",
            )
            val label = if (at != null) {
                "✓ ${step.title} " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
            } else step.title
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    at != null -> Color(0xFFFFD9A6)
                    step.id == current -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .background(bg, shape)
                    .then(if (step.id == current) Modifier.border(1.5.dp, Palette.Sun, shape) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Outside the lock window: when the next morning starts, how you are doing, and untested steps. */
@Composable
private fun RestView(data: AppData, now: LocalDateTime, streak: Int, onTestNow: () -> Unit) {
    val s = data.settings
    val today = now.toLocalDate()
    val routineToday = Routine.isRoutineDay(s, today)
    val beforeStart = routineToday && Routine.minutesOfDay(now) < s.wakeMinutes
    val night = now.hour >= 20 || now.hour < 5

    Text(if (night || beforeStart) "☾" else "☀", fontSize = 56.sp, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(12.dp))
    when {
        beforeStart -> {
            Big(if (night) "Sleep well" else "Rest up")
            Small("Your morning starts at ${minutesToText(s.wakeMinutes)}.")
        }
        routineToday -> {
            val log = data.logs[today.toString()]
            val t = log?.completedAtMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
            }
            when (Routine.dayStatus(data, today, now)) {
                Routine.DayStatus.OnTime -> { Big("Good morning, done"); Small("Today you finished at $t. Nice work.") }
                Routine.DayStatus.Late -> { Big("You did it"); Small("Today you finished at $t, after the lock ended.") }
                Routine.DayStatus.Skipped -> { Big("Today's morning is over"); Small("You skipped today. Tomorrow is a new morning.") }
                Routine.DayStatus.Away -> { Big("No lock today"); Small("You were away from home this morning.") }
                Routine.DayStatus.LockOff -> { Big("Today's morning is over"); Small("The lock was off for part of this morning, so it does not count for your streak.") }
                else -> { Big("Today's morning is over"); Small("Tomorrow is a new morning.") }
            }
        }
        else -> {
            Big("No lock today")
            Small("Enjoy your day off.")
        }
    }
    if (!beforeStart) {
        Routine.nextLockStart(s, now)?.let { next ->
            val day = when (next.toLocalDate()) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> next.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            }
            Small("Next morning: $day, ${next.toLocalTime().format(timeFmt)}")
        }
    }
    if (streak > 0) {
        Spacer(Modifier.height(24.dp))
        Text("$streak", fontSize = 44.sp, fontWeight = FontWeight.Light)
        Small(if (streak == 1) "good morning in a row" else "good mornings in a row")
    }
    Spacer(Modifier.height(20.dp))
    MiniWeek(data, now, animateToday = false, letters = true)
    Text(
        "Bright: early · Blue: away · Red: lock off · Outline: missed",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    val untested = data.steps.count { it.testedAtMillis == null }
    if (untested > 0) {
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onTestNow) {
            Text(if (untested == 1) "1 step not tested · Test now" else "$untested steps not tested · Test now")
        }
    }
}

/** Skipping must be slow, so you do it only when you really need your phone. */
@Composable
private fun HoldToSkip(onSkip: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (progress.value > 0f) "Keep holding to unlock for today…" else "Emergency? Hold here for 10 seconds to skip today",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val job = scope.launch {
                            progress.animateTo(1f, tween(10_000, easing = LinearEasing))
                            onSkip()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) job.cancel()
                        scope.launch { progress.snapTo(0f) }
                    })
                }
                .padding(12.dp),
        )
        if (progress.value > 0f) {
            LinearProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun Big(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineLarge,
    fontFamily = Palette.Serif,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onBackground,
)

@Composable
private fun Small(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 6.dp),
)

private fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun inWindowNow(data: AppData, now: LocalDateTime) = data.steps.isNotEmpty() && Routine.inMorningWindow(data.settings, now)

/** During the morning, a lock that is off is a problem. Say so clearly and give a one-tap fix. */
@Composable
private fun LockOffCard() {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("The morning lock is off", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Your apps are not blocked. While the lock is off, this morning does not count for your streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Button(onClick = {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Turn on the lock") }
        }
    }
}
