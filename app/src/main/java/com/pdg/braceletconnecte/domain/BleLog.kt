package com.pdg.braceletconnecte.domain

import android.util.Log

/**
 * Logging for the BLE link, to logcat only.
 *
 * We keep the `[TAG] message` convention already used throughout the repo and on
 * the firmware side: both traces read side by side.
 *
 *   [SYNC] Packet #3 received (20 measurements) -> inserted 18 / ignored 2 (already seen) -> ACK
 *
 * Tags: BLE (link), SYNC (protocol), STATE (full state), Storage (local
 * database), Upload (backend upload).
 */
// Log for debugging: every line below goes to logcat only, never to the UI.
object BleLog {
    const val TAG = "BRASCO"

    fun i(tag: String, message: String) = Log.i(TAG, format('I', tag, message))

    fun w(tag: String, message: String) = Log.w(TAG, format('W', tag, message))

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        Log.e(TAG, format('E', tag, message), throwable)

    private fun format(level: Char, tag: String, message: String): String {
        // The level only shows when it is out of the ordinary: every other line
        // prefixed "I/" says nothing, a "[!]" in front of a warning does.
        val prefix = if (level == 'I') "" else "[!] "
        return "$prefix[$tag] $message"
    }
}
