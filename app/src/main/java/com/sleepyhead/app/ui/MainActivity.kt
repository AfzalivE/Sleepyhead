package com.sleepyhead.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleepyhead.app.SleepyheadApp
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.data.Store
import com.sleepyhead.app.lock.LockHealth
import com.sleepyhead.app.lock.LockService
import com.sleepyhead.app.nfc.NfcEvent
import com.sleepyhead.app.nfc.NfcHub
import com.sleepyhead.app.travel.TravelCheck
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    /** The app the lock sent you away from, shown once on the calm screen. */
    private val blockedFrom = mutableStateOf<String?>(null)
    private val store get() = (application as SleepyheadApp).store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so the system bars always use light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        blockedFrom.value = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        applyDefaultBlockedApps(store)
        if (savedInstanceState == null) {
            val tap = NfcHub.handleIntent(this, store, intent)
            // A tag for a step that is already done opened the app. Ignore the tap: close again.
            if (tap != null && NfcHub.isIgnored(tap)) {
                finish()
                return
            }
        }
        setContent {
            SleepyheadTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(store, blockedFrom.value) { blockedFrom.value = null }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NfcHub.enableReader(this, store)
        TravelCheck.runIfNeeded(this, store)
        // If the lock service is not running now, the time since it was last seen is a gap.
        if (!LockService.running || !LockService.isEnabled(this)) LockHealth.recordGap(this, store)
    }

    override fun onPause() {
        NfcHub.disableReader(this)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.let { blockedFrom.value = it }
        val tap = NfcHub.handleIntent(this, store, intent)
        // A tag for a step that is already done brought the app to the front. Ignore the tap: go back.
        if (tap != null && NfcHub.isIgnored(tap)) moveTaskToBack(true)
    }

    /** On first launch, pre-select common news, social and work chat apps that are installed. */
    private fun applyDefaultBlockedApps(store: Store) {
        if (store.data.value.settings.defaultsApplied) return
        val installed = InstalledApps.load(this).map { it.packageName }.toSet()
        val picks = InstalledApps.SUGGESTED.filter { it in installed }.toSet()
        store.updateSettings { it.copy(blockedPackages = picks, defaultsApplied = true) }
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        /** Sent as the blocked package when the lock sends you back from Sleepyhead's own settings pages. */
        const val BLOCKED_SETTINGS = "sleepyhead.settings"
    }
}

enum class Screen { Home, Settings, Apps, Steps, Codes, History }

@Composable
fun App(store: Store, blockedFrom: String?, onBlockedSeen: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val data by store.data.collectAsStateWithLifecycle()
    val nfcEvent by NfcHub.event.collectAsStateWithLifecycle()
    val go: (Screen) -> Unit = { screen = it }

    // A blocked app always brings you back to the calm screen.
    LaunchedEffect(blockedFrom) { if (blockedFrom != null) screen = Screen.Home }

    if (screen != Screen.Home) {
        BackHandler { screen = if (screen in listOf(Screen.Apps, Screen.Steps)) Screen.Settings else Screen.Home }
    }
    // On wide screens (tablets, unfolded foldables), keep content in a centered column.
    val frame = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 600.dp)
        .fillMaxWidth()

    // The sky behind Home: night outside the lock, lighter with each step, full sunrise when done.
    var skyNow by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); skyNow = LocalDateTime.now() } }
    val onHome = screen == Screen.Home && data.settings.onboarded
    val level = if (onHome) skyLevel(data, skyNow) else 0f
    Box(Modifier.fillMaxSize()) {
        if (onHome) SkyBackground(level)
        SunriseInk(on = onHome && level >= 2f) {
            Column(frame) {
                nfcEvent?.let { e ->
                    Notice(nfcMessage(e), Modifier.padding(horizontal = 16.dp)) { NfcHub.clearEvent() }
                }
                val m = Modifier.fillMaxWidth().weight(1f)
                if (!data.settings.onboarded) {
                    OnboardingScreen(store, data, m)
                    return@Column
                }
                when (screen) {
                    Screen.Home -> HomeScreen(store, data, blockedFrom, onBlockedSeen, go, m)
                    Screen.Settings -> SettingsScreen(store, data, go, m)
                    Screen.Apps -> AppsScreen(store, data, { screen = Screen.Settings }, m)
                    Screen.Steps -> StepsScreen(store, data, { screen = Screen.Settings }, m)
                    Screen.Codes -> CodesScreen(data, { screen = Screen.Home }, m)
                    Screen.History -> HistoryScreen(data, { screen = Screen.Home }, m)
                }
            }
        }
    }
}

fun proofMessage(r: Routine.ProofResult): String = when (r) {
    is Routine.ProofResult.Completed -> Encouragement.step(r.step.title, r.remaining, r.step.id.hashCode() + r.remaining)
    is Routine.ProofResult.WrongOrder ->
        "That is the ${r.scanned.place} step. First: ${r.expected.title} (${r.expected.place})."
    is Routine.ProofResult.AlreadyDone -> "\"${r.step.title}\" is already done today."
    Routine.ProofResult.NothingLeft -> "Your routine is already done today."
    is Routine.ProofResult.Tested -> "This is the code for \"${r.step.title}\". It does not count now, because the morning lock is not on. To check your codes, use Test in Settings → Steps."
    Routine.ProofResult.Unknown -> "This is not one of your Sleepyhead codes or tags."
}

fun nfcMessage(e: NfcEvent): String = when (e) {
    is NfcEvent.Test -> when (val r = e.result) {
        Routine.TestResult.Works -> "✓ The ${e.tested.place} code works for \"${e.tested.title}\"."
        is Routine.TestResult.OtherStep -> "That is the ${r.step.place} code (\"${r.step.title}\"), not ${e.tested.place}."
        Routine.TestResult.Unknown -> "Not a current Sleepyhead code. Print or pair \"${e.tested.title}\" again."
    }
    is NfcEvent.Proof -> proofMessage(e.result)
    is NfcEvent.Paired ->
        if (e.wroteTag) "Tag paired with \"${e.stepTitle}\". A tap now opens Sleepyhead, even when it is closed."
        else "Tag paired with \"${e.stepTitle}\". Open Sleepyhead before you tap it (this tag cannot be written)."
}

@Composable
fun SleepyheadTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFFF2B66D),
        onPrimary = Color(0xFF2A1B05),
        secondary = Color(0xFF8FA3C7),
        background = Color(0xFF12161F),
        onBackground = Color(0xFFE8ECF4),
        surface = Color(0xFF12161F),
        onSurface = Color(0xFFE8ECF4),
        surfaceVariant = Color(0xFF1E2430),
        onSurfaceVariant = Color(0xFFB4BCCB),
        surfaceContainer = Color(0xFF1A1F2A),
        surfaceContainerHigh = Color(0xFF222836),
    )
    MaterialTheme(colorScheme = colors, content = content)
}
