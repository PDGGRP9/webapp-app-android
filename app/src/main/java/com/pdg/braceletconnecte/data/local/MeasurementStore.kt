package com.pdg.braceletconnecte.data.local

import com.pdg.braceletconnecte.domain.BiometricMeasurement
import com.pdg.braceletconnecte.domain.BleLog
import com.pdg.braceletconnecte.domain.BraceletMeasurementCodec.PLAUSIBLE_HR
import com.pdg.braceletconnecte.domain.BraceletMeasurementCodec.PLAUSIBLE_SPO2

/**
 * Single entry point to the local database.
 *
 * The BLE link writes here and never waits for the network: the ACK is sent as
 * soon as the measurement is stored. The uploader deals with the backend, at its
 * own pace.
 */
class MeasurementStore(private val dao: MeasurementDao) {

    /** Returns how many measurements were actually new (the rest were duplicates). */
    suspend fun save(measurements: List<BiometricMeasurement>): Int {
        if (measurements.isEmpty()) return 0
        val rows = measurements.map { it.toEntity() }
        val inserted = dao.insertAll(rows).count { it != -1L }
        val ignored = measurements.size - inserted
        if (ignored > 0) {
            // Log for debugging
            BleLog.i("Storage", "$inserted insérées / $ignored déjà connues (dédup par ts)")
        }
        return inserted
    }

    suspend fun pendingToUpload(limit: Int = 50): List<MeasurementEntity> = dao.pendingToUpload(limit)

    suspend fun markSent(entity: MeasurementEntity) = dao.markSent(entity.deviceUid, entity.ts)
}

fun BiometricMeasurement.toEntity(): MeasurementEntity = MeasurementEntity(
    deviceUid = deviceUid,
    ts = capturedAt.epochSecond,
    serialNumber = serialNumber,
    deviceName = deviceName,
    macAddress = macAddress,
    heartRateBpm = heartRateBpm,
    spo2Percent = spo2Percent,
    stepCount = stepCount,
    sent = false,
)

fun MeasurementEntity.toDomain(): BiometricMeasurement = BiometricMeasurement(
    deviceUid = deviceUid,
    serialNumber = serialNumber,
    deviceName = deviceName,
    macAddress = macAddress,
    capturedAt = java.time.Instant.ofEpochSecond(ts),
    // Same filter as the live path (BraceletMeasurementCodec.toMeasurement): rows
    // written before this filter existed still carry the sensor's 255 when no
    // finger is on it. The backend rejects that with a 500, and since the queue is
    // drained by ascending timestamp, one such row blocks all the others.
    heartRateBpm = heartRateBpm?.takeIf { it in PLAUSIBLE_HR },
    spo2Percent = spo2Percent?.takeIf { it.toInt() in PLAUSIBLE_SPO2 },
    stepCount = stepCount,
    rawPayload = ByteArray(0),
)
