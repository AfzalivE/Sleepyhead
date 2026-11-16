package com.sleepyhead.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sleepyhead.app.SleepyheadApp
import com.sleepyhead.app.data.Store

class MainActivity : ComponentActivity() {

    /** The app the lock sent you away from, shown once on the calm screen. */
    private val blockedFrom = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        blockedFrom.value = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        val store = (application as SleepyheadApp).store
        applyDefaultBlockedApps(store)
        setContent {
            SleepyheadTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(store, blockedFrom.value) { blockedFrom.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.let { blockedFrom.value = it }
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
    }
}

enum class Screen { Home, Settings, Apps, Steps, Codes, History }

@Composable
fun App(store: Store, blockedFrom: String?, onBlockedSeen: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val data by store.data.collectAsStateWithLifecycle()
    val go: (Screen) -> Unit = { screen = it }

    // A blocked app always brings you back to the calm screen.
    LaunchedEffect(blockedFrom) { if (blockedFrom != null) screen = Screen.Home }

    if (screen != Screen.Home) {
        BackHandler { screen = if (screen in listOf(Screen.Apps, Screen.Steps)) Screen.Settings else Screen.Home }
    }
    val m = Modifier.fillMaxSize().safeDrawingPadding()
    when (screen) {
        Screen.Home -> HomeScreen(store, data, blockedFrom, onBlockedSeen, go, m)
        Screen.Settings -> SettingsScreen(store, data, go, m)
        Screen.Apps -> AppsScreen(store, data, { screen = Screen.Settings }, m)
        Screen.Steps -> StepsScreen(store, data, { screen = Screen.Settings }, m)
        Screen.Codes -> CodesScreen(data, { screen = Screen.Home }, m)
        Screen.History -> HistoryScreen(data, { screen = Screen.Home }, m)
    }
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
