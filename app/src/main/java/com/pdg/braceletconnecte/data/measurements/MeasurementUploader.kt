package com.pdg.braceletconnecte.data.measurements

import com.pdg.braceletconnecte.data.auth.AuthRepository
import com.pdg.braceletconnecte.data.auth.AuthState
import com.pdg.braceletconnecte.data.local.MeasurementStore
import com.pdg.braceletconnecte.data.local.toDomain
import com.pdg.braceletconnecte.domain.BleLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

/**
 * Drains the local database to the backend, in the background.
 *
 * Deliberately decoupled from BLE: the bracelet sync must never wait for the
 * network. If the backend is unreachable, measurements pile up in the database
 * and go out again once the connection is back — the user loses nothing and the
 * bracelet has already flushed its own stock.
 *
 * Two points are worth knowing:
 *  - [nudge] wakes the loop as soon as a measurement is stored, otherwise live
 *    data would wait for the next tick (up to 5 s) before going out.
 *  - a measurement the backend rejects with a 4xx is dropped instead of being
 *    retried forever: the queue is ordered by timestamp, so a single rejected
 *    measurement would block every one after it.
 */
class MeasurementUploader(
    private val store: MeasurementStore,
    private val repository: MeasurementsRepository,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope,
) {
    private var started = false

    // A single measurement failing over and over: the queue is ordered by
    // timestamp, so as long as we retry it nothing behind it gets through. After
    // MAX_ATTEMPTS we drop it to unblock the rest.
    private var lastFailedKey: String? = null
    private var failStreak = 0

    // CONFLATED: ten measurements in a row trigger one pass, not ten.
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /** A measurement was just written to the database: don't wait for the tick. */
    fun nudge() {
        wakeUp.trySend(Unit)
    }

    fun start() {
        if (started) return
        started = true

        scope.launch {
            while (isActive) {
                val sleep = uploadOnce()
                // sleep == 0: the batch was full, keep going without a pause.
                if (sleep > 0) withTimeoutOrNull(sleep) { wakeUp.receive() }
            }
        }
    }

    /** Returns how long to wait before the next pass. */
    private suspend fun uploadOnce(): Long {
        // No session = no token: pointless to try, we'll come back later.
        if (authRepository.authState.value !is AuthState.LoggedIn) return IDLE_DELAY_MS

        val batch = store.pendingToUpload(BATCH_SIZE)
        if (batch.isEmpty()) return IDLE_DELAY_MS

        var sent = 0
        for (entity in batch) {
            val error = repository.postMeasurement(entity.toDomain()).exceptionOrNull()
            when {
                error == null -> {
                    store.markSent(entity)
                    sent++
                    lastFailedKey = null
                    failStreak = 0
                }

                // 4xx (except 401): the backend will reject this measurement on
                // every attempt. Take it out of the queue, otherwise it blocks
                // every newer measurement forever.
                error is HttpException && error.code() != 401 && error.code() in 400..499 -> {
                    warn(
                        "mesure ts=${entity.ts} refusée (HTTP ${error.code()} : " +
                            "${repository.errorDetail(error) ?: "sans détail"}) -> abandonnée",
                    )
                    store.markSent(entity)
                }

                // Network down, backend broken, 401: we'll retry later, nothing
                // is lost (measurements stay in the database).
                // The backend answered, but with an error (typically 500). We
                // retry — unless it is always the same measurement failing: in
                // that case the measurement is the problem, not the server.
                else -> {
                    val key = "${entity.deviceUid}@${entity.ts}"
                    if (key == lastFailedKey) failStreak++ else { lastFailedKey = key; failStreak = 1 }

                    if (error is HttpException && failStreak >= MAX_ATTEMPTS_PER_MEASUREMENT) {
                        warn(
                            "mesure ts=${entity.ts} en échec $failStreak fois (HTTP ${error.code()} : " +
                                "${repository.errorDetail(error) ?: "sans détail"}) -> abandonnée",
                        )
                        store.markSent(entity)
                        lastFailedKey = null
                        failStreak = 0
                        continue
                    }

                    warn(
                        "envoi interrompu après $sent/${batch.size} : ${error.message} " +
                            "-> ${batch.size - sent} mesures restent en base",
                    )
                    return RETRY_DELAY_MS
                }
            }
        }

        info("$sent mesures envoyées au backend")
        // There may be more waiting: keep going quickly while it works.
        return if (batch.size == BATCH_SIZE) 0L else IDLE_DELAY_MS
    }

    // Log for debugging
    private fun info(message: String) = BleLog.i("Upload", message)

    // Log for debugging
    private fun warn(message: String) = BleLog.w("Upload", message)

    private companion object {
        const val BATCH_SIZE = 50

        /** Past this, the measurement is considered the problem, not the server. */
        const val MAX_ATTEMPTS_PER_MEASUREMENT = 5
        const val IDLE_DELAY_MS = 5_000L
        const val RETRY_DELAY_MS = 15_000L
    }
}
