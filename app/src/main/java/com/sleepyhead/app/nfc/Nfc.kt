package com.sleepyhead.app.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import com.sleepyhead.app.data.Routine
import com.sleepyhead.app.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What happened when a tag was read. The UI shows it as a message. */
sealed interface NfcEvent {
    data class Proof(val result: Routine.ProofResult) : NfcEvent
    data class Paired(val stepTitle: String, val wroteTag: Boolean) : NfcEvent
    data class Test(val tested: com.sleepyhead.app.data.Step, val result: Routine.TestResult) : NfcEvent
}

/**
 * Routes NFC tags. While a step is waiting to be paired, the next tag is paired with it.
 * Otherwise a tag finishes the step it belongs to.
 */
object NfcHub {
    /** MIME type of the record that Sleepyhead writes to a paired tag. Lowercase, as Android requires. */
    const val MIME = "application/vnd.com.sleepyhead.step"

    private val _pairing = MutableStateFlow<String?>(null)
    val pairing: StateFlow<String?> = _pairing.asStateFlow()

    /** The latest tag result. The UI shows it until [clearEvent]. */
    private val _event = MutableStateFlow<NfcEvent?>(null)
    val event: StateFlow<NfcEvent?> = _event.asStateFlow()

    fun clearEvent() { _event.value = null }

    private val _testing = MutableStateFlow<String?>(null)
    /** The step being tested in Settings. While set, a tag is only checked, never counted. */
    val testing: StateFlow<String?> = _testing.asStateFlow()

    fun startTesting(stepId: String) { _testing.value = stepId }
    fun stopTesting() { _testing.value = null }

    /** Shows a test result and remembers a passed test. Used for both QR and NFC tests. */
    fun reportTest(store: Store, stepId: String, found: com.sleepyhead.app.data.Step?) {
        val data = store.data.value
        val tested = data.steps.firstOrNull { it.id == stepId } ?: return
        val result = Routine.testStep(data, stepId, found)
        if (result == Routine.TestResult.Works) store.markTested(stepId)
        _testing.value = null
        _event.value = NfcEvent.Test(tested, result)
    }

    fun startPairing(stepId: String) { _pairing.value = stepId }
    fun cancelPairing() { _pairing.value = null }

    fun isAvailable(context: Context) = NfcAdapter.getDefaultAdapter(context) != null
    fun isEnabled(context: Context) = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

    /** Reads tags while [activity] is in front, so the system does not open other apps for them. */
    fun enableReader(activity: Activity, store: Store) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        runCatching { adapter.enableReaderMode(activity, { tag -> onTag(activity, store, tag) }, flags, null) }
    }

    fun disableReader(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    /**
     * True for a tag tap that changes nothing because the step (or the whole routine) is already done today.
     * Sleepyhead ignores these taps: it shows no message.
     */
    fun isIgnored(result: Routine.ProofResult) =
        result is Routine.ProofResult.AlreadyDone || result == Routine.ProofResult.NothingLeft

    /**
     * Handles a tag that opened the app from the background (NDEF_DISCOVERED).
     * Returns the result, or null if [intent] is not a tag.
     */
    fun handleIntent(context: Context, store: Store, intent: Intent?): Routine.ProofResult? {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        val tagId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)?.toHex()
        val messages = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>()?.toTypedArray()
        }
        val code = messages?.firstNotNullOfOrNull { codeFrom(it) }
        val result = store.tapTag(tagId, code)
        if (!isIgnored(result)) _event.value = NfcEvent.Proof(result)
        return result
    }

    private fun onTag(context: Context, store: Store, tag: Tag) {
        val tagId = tag.id?.toHex() ?: return
        val pairingId = _pairing.value
        if (pairingId != null) {
            val step = store.data.value.steps.firstOrNull { it.id == pairingId } ?: return
            store.pairTag(step.id, tagId)
            val wrote = writeCode(context, tag, step.code)
            _pairing.value = null
            _event.value = (NfcEvent.Paired(step.title, wrote))
            return
        }
        val code = runCatching { Ndef.get(tag)?.cachedNdefMessage?.let { codeFrom(it) } }.getOrNull()
        val testingId = _testing.value
        if (testingId != null) {
            val data = store.data.value
            reportTest(store, testingId, Routine.stepForTag(data, tagId) ?: Routine.stepForCode(data, code))
            return
        }
        val result = store.tapTag(tagId, code)
        if (!isIgnored(result)) _event.value = NfcEvent.Proof(result)
    }

    private fun codeFrom(message: NdefMessage): String? =
        message.records.firstOrNull { it.tnf == NdefRecord.TNF_MIME_MEDIA && String(it.type) == MIME }
            ?.payload?.let { String(it, Charsets.UTF_8) }

    /**
     * Writes the step code and an app record to the tag, so a tap opens Sleepyhead even when it is closed.
     * Pairing works without this (by tag ID), so a read-only tag is fine.
     */
    private fun writeCode(context: Context, tag: Tag, code: String): Boolean = runCatching {
        val msg = NdefMessage(
            arrayOf(
                NdefRecord.createMime(MIME, code.toByteArray(Charsets.UTF_8)),
                NdefRecord.createApplicationRecord(context.packageName),
            )
        )
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                if (!it.isWritable || it.maxSize < msg.toByteArray().size) return@runCatching false
                it.writeNdefMessage(msg)
            }
            true
        } else {
            val f = NdefFormatable.get(tag) ?: return@runCatching false
            f.use { it.connect(); it.format(msg) }
            true
        }
    }.getOrDefault(false)

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
