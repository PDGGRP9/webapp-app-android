package com.pdg.braceletconnecte.data.local

import androidx.room.Entity

/**
 * A measurement stored on the phone, waiting to be sent to the backend.
 *
 * The `(deviceUid, ts)` primary key handles the whole deduplication of the
 * protocol: if the bracelet resends an unacknowledged packet, already known
 * measurements are simply ignored on insert (OnConflictStrategy.IGNORE). No test
 * to write, no error to handle — exactly what the README asks for
 * ("an already seen packet = ignored, not an error").
 */
@Entity(tableName = "measurements", primaryKeys = ["deviceUid", "ts"])
data class MeasurementEntity(
    val deviceUid: String,
    /** epoch UTC in seconds */
    val ts: Long,
    val serialNumber: String,
    val deviceName: String,
    val macAddress: String,
    val heartRateBpm: Int?,
    val spo2Percent: Double?,
    val stepCount: Long,
    /** false until the backend has accepted it. */
    val sent: Boolean = false,
)
