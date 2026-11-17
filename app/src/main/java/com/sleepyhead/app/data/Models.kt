package com.sleepyhead.app.data

import kotlinx.serialization.Serializable

/** One step of the morning routine. You finish it by scanning its QR [code] or tapping its NFC tag. */
@Serializable
data class Step(
    val id: String,
    val title: String,
    val place: String,
    /** The text inside the printed QR code for this step. It is also written to a paired NFC tag. */
    val code: String,
    /** Hex ID of the NFC tag paired with this step, or null. */
    val nfcTagId: String? = null,
)

@Serializable
data class HomeLocation(val lat: Double, val lon: Double)

@Serializable
data class Settings(
    /** Minutes after midnight when the morning lock starts. */
    val wakeMinutes: Int = 6 * 60 + 30,
    /** Minutes after midnight when the lock stops. Finishing before then counts for your streak. */
    val lockEndMinutes: Int = 9 * 60,
    /** ISO days of week (1 = Monday ... 7 = Sunday) when the routine runs. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Package names of apps that are blocked during the morning lock. */
    val blockedPackages: Set<String> = emptySet(),
    /** False until the first launch has applied the default blocked apps. */
    val defaultsApplied: Boolean = false,
    /** False until onboarding is finished. */
    val onboarded: Boolean = false,
    /** Travel mode: at lock start, if the phone is away from [home], the lock stays off that day. */
    val autoTravel: Boolean = false,
    val home: HomeLocation? = null,
    val homeRadiusMeters: Int = 2000,
)

@Serializable
data class StepDone(val stepId: String, val atMillis: Long)

/** The record of one morning. [date] is ISO yyyy-MM-dd. */
@Serializable
data class DayLog(
    val date: String,
    val done: List<StepDone> = emptyList(),
    val completedAtMillis: Long? = null,
    /** True when the routine was finished before the lock ended. */
    val onTime: Boolean = false,
    val skipped: Boolean = false,
    /** How many times the lock sent you back from a blocked app this morning. */
    val blockedAttempts: Int = 0,
    /** Result of the travel check at lock start: true = away from home, null = not checked. */
    val away: Boolean? = null,
)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val steps: List<Step> = emptyList(),
    val logs: Map<String, DayLog> = emptyMap(),
)
