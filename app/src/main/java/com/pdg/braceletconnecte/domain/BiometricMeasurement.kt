package com.pdg.braceletconnecte.domain

import java.time.Instant

data class BiometricMeasurement(
    val deviceUid: String,
    val serialNumber: String,
    val deviceName: String,
    val macAddress: String,
    val capturedAt: Instant,
    val heartRateBpm: Int? = null,
    val spo2Percent: Double? = null,
    val stepCount: Long = 0,
    val motionLevel: Double? = null,
    val signalQuality: Int? = null,
    val rawPayload: ByteArray,
)
