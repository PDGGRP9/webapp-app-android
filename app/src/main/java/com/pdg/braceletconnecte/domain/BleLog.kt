package com.pdg.braceletconnecte.domain

import android.util.Log

/**
 * Logging for the BLE link.
 *
 * Every line goes to two places: logcat (for whoever has a USB cable) and the
 * log shown inside the app (for whoever has none and still needs to describe
 * their problem). A single method does both: it writes to logcat and returns the
 * formatted line, which the caller pushes into the UI.
 *
 * We keep the `[TAG] message` convention already used throughout the repo and on
 * the firmware side: both traces read side by side.
 *
 *   [SYNC] Packet #3 received (20 measurements) -> inserted 18 / ignored 2 (already seen) -> ACK
 *
 * Tags: BLE (link), SYNC (protocol), STATE (full state), Storage (local
 * database), Upload (backend upload).
 */
object BleLog {
    const val TAG = "BRASCO"

    fun i(tag: String, message: String): String = emit('I', tag, message)
    fun w(tag: String, message: String): String = emit('W', tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null): String {
        val line = format('E', tag, message)
        Log.e(TAG, line, throwable)
        return line
    }

    private fun emit(level: Char, tag: String, message: String): String {
        val line = format(level, tag, message)
        when (level) {
            'W' -> Log.w(TAG, line)
            else -> Log.i(TAG, line)
        }
        return line
    }

    private fun format(level: Char, tag: String, message: String): String {
        // The level only shows when it is out of the ordinary: every other line
        // prefixed "I/" says nothing, a "[!]" in front of a warning does.
        val prefix = if (level == 'I') "" else "[!] "
        return "$prefix[$tag] $message"
    }
}
