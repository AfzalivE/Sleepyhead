package com.sleepyhead.app.data

import kotlinx.serialization.Serializable

/** One step of the morning routine. It is completed by scanning [code]. */
@Serializable
data class Step(
    val id: String,
    val title: String,
    val place: String,
    /** The text inside the printed QR code for this step. */
    val code: String,
)

@Serializable
data class Settings(
    /** Minutes after midnight when the morning lock starts. */
    val wakeMinutes: Int = 6 * 60 + 30,
    /** Minutes after midnight when you want to be at your desk. Finishing by then counts as on time. */
    val targetMinutes: Int = 8 * 60,
    /** Minutes after midnight when the lock stops, even if the routine is not done. */
    val lockEndMinutes: Int = 11 * 60,
    /** ISO days of week (1 = Monday ... 7 = Sunday) when the routine runs. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Package names of apps that are blocked during the morning lock. */
    val blockedPackages: Set<String> = emptySet(),
    /** False until the first launch has applied the default blocked apps. */
    val defaultsApplied: Boolean = false,
)

@Serializable
data class StepDone(val stepId: String, val atMillis: Long)

/** The record of one morning. [date] is ISO yyyy-MM-dd. */
@Serializable
data class DayLog(
    val date: String,
    val done: List<StepDone> = emptyList(),
    val completedAtMillis: Long? = null,
    val onTime: Boolean = false,
    val skipped: Boolean = false,
    /** How many times the lock sent you back from a blocked app this morning. */
    val blockedAttempts: Int = 0,
)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val steps: List<Step> = emptyList(),
    val logs: Map<String, DayLog> = emptyMap(),
)
