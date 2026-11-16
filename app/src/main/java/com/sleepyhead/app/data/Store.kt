package com.sleepyhead.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps all app data in one JSON document in SharedPreferences.
 * The data is small (settings, a few steps, one log entry per day), so this is enough.
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("sleepyhead", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun load(): AppData {
        val raw = prefs.getString(KEY, null)
        val loaded = raw?.let { runCatching { json.decodeFromString<AppData>(it) }.getOrNull() }
        return loaded ?: AppData(steps = defaultSteps()).also { save(it) }
    }

    private fun save(d: AppData) {
        prefs.edit().putString(KEY, json.encodeToString(AppData.serializer(), d)).apply()
    }

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        if (next != _data.value) {
            _data.value = next
            save(next)
        }
    }

    fun isLockActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        Routine.isLockActive(_data.value, now)

    @Synchronized
    fun scan(text: String, now: LocalDateTime = LocalDateTime.now()): Routine.ScanResult {
        val (next, result) = Routine.applyScan(_data.value, text, now, ZoneId.systemDefault())
        update { next }
        return result
    }

    fun skipToday(now: LocalDateTime = LocalDateTime.now()) = updateToday(now) { it.copy(skipped = true) }

    fun undoSkipToday(now: LocalDateTime = LocalDateTime.now()) = updateToday(now) { it.copy(skipped = false) }

    fun countBlockedAttempt(now: LocalDateTime = LocalDateTime.now()) =
        updateToday(now) { it.copy(blockedAttempts = it.blockedAttempts + 1) }

    fun resetToday(now: LocalDateTime = LocalDateTime.now()) = update {
        it.copy(logs = it.logs - now.toLocalDate().toString())
    }

    private fun updateToday(now: LocalDateTime, f: (DayLog) -> DayLog) = update {
        val key = now.toLocalDate().toString()
        val log = it.logs[key] ?: DayLog(date = key)
        it.copy(logs = it.logs + (key to f(log)))
    }

    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    fun setSteps(steps: List<Step>) = update { it.copy(steps = steps) }

    fun newStep(title: String, place: String): Step = Step(id = randomToken(6), title = title, place = place, code = newCode())

    /** Makes new codes for every step. Old printed codes stop working. */
    fun regenerateCodes() = update { d -> d.copy(steps = d.steps.map { it.copy(code = newCode()) }) }

    fun today(): LocalDate = LocalDate.now()

    private fun defaultSteps() = listOf(
        newStep("Brush your teeth", "Bathroom"),
        newStep("Eat breakfast", "Kitchen"),
        newStep("Sit down at your desk", "Home office desk"),
    )

    private fun newCode() = "SLEEPYHEAD:" + randomToken(16)

    private fun randomToken(len: Int): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..len).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    companion object {
        private const val KEY = "data_v1"
    }
}
