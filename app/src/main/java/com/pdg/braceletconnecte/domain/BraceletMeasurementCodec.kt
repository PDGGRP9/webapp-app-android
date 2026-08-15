package com.pdg.braceletconnecte.domain

import android.util.Base64
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

object BraceletMeasurementCodec {

    fun decode(identity: BraceletIdentity, payload: ByteArray): BiometricMeasurement {
        decodeJson(identity, payload)?.let { return it }
        // firmware/ (branch core-firmware) always sends a fixed 4-byte struct: check this
        // deterministically before the delimited-text heuristic, which could otherwise
        // misfire if a raw byte happens to equal ',' / ';' / '|'.
        if (payload.size == 4) {
            return decodeCoreFirmwareBinary(identity, payload)
        }
        decodeDelimitedText(identity, payload)?.let { return it }
        return decodeBinary(identity, payload)
    }

    private fun decodeJson(identity: BraceletIdentity, payload: ByteArray): BiometricMeasurement? {
        val text = payload.toString(Charsets.UTF_8).trim()
        if (text.isEmpty() || !text.startsWith("{") || !text.endsWith("}")) {
            return null
        }

        return runCatching {
            val json = JSONObject(text)
            BiometricMeasurement(
                deviceUid = json.optString("device_uid", identity.deviceUid),
                serialNumber = json.optString("serial_number", identity.serialNumber),
                deviceName = json.optString("device_name", identity.deviceName),
                macAddress = json.optString("mac_address", identity.macAddress),
                capturedAt = parseInstant(json.optString("captured_at", "")),
                heartRateBpm = json.takeIf { it.has("heart_rate_bpm") && !it.isNull("heart_rate_bpm") }?.optInt("heart_rate_bpm"),
                spo2Percent = json.takeIf { it.has("spo2_percent") && !it.isNull("spo2_percent") }?.optDouble("spo2_percent"),
                stepCount = json.optLong("step_count", 0L),
                motionLevel = json.takeIf { it.has("motion_level") && !it.isNull("motion_level") }?.optDouble("motion_level"),
                signalQuality = json.takeIf { it.has("signal_quality") && !it.isNull("signal_quality") }?.optInt("signal_quality"),
                rawPayload = payload,
            )
        }.getOrNull()
    }

    private fun decodeDelimitedText(identity: BraceletIdentity, payload: ByteArray): BiometricMeasurement? {
        val text = payload.toString(Charsets.UTF_8).trim()
        if (text.isEmpty() || (',' !in text && ';' !in text && '|' !in text)) {
            return null
        }

        val parts = text.split(',', ';', '|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return null
        }

        val heartRate = parts.getOrNull(0)?.toIntOrNull()
        val spo2 = parts.getOrNull(1)?.toDoubleOrNull()
        val steps = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val motion = parts.getOrNull(3)?.toDoubleOrNull()
        val signal = parts.getOrNull(4)?.toIntOrNull()

        return BiometricMeasurement(
            deviceUid = identity.deviceUid,
            serialNumber = identity.serialNumber,
            deviceName = identity.deviceName,
            macAddress = identity.macAddress,
            capturedAt = Instant.now(),
            heartRateBpm = heartRate,
            spo2Percent = spo2,
            stepCount = steps,
            motionLevel = motion,
            signalQuality = signal,
            rawPayload = payload,
        )
    }

    /**
     * firmware/ (branch core-firmware) src/ble_manager.cpp BLEManager::sendSensorData:
     * payload = [HR uint8][SpO2 uint8][Steps MSB][Steps LSB], steps as a big-endian uint16.
     * The DFRobot MAX30102 (also used on the sibling iOS-firmware branch) reports 0 for
     * "no reading" rather than a real 0 bpm/percent, so 0 is surfaced as null (shown as "—").
     */
    private fun decodeCoreFirmwareBinary(identity: BraceletIdentity, payload: ByteArray): BiometricMeasurement {
        val heartRate = payload[0].toUByte().toInt()
        val spo2 = payload[1].toUByte().toInt()
        val steps = (payload[2].toUByte().toInt() shl 8) or payload[3].toUByte().toInt()

        return BiometricMeasurement(
            deviceUid = identity.deviceUid,
            serialNumber = identity.serialNumber,
            deviceName = identity.deviceName,
            macAddress = identity.macAddress,
            capturedAt = Instant.now(),
            heartRateBpm = heartRate.takeIf { it > 0 },
            spo2Percent = spo2.takeIf { it > 0 }?.toDouble(),
            stepCount = steps.toLong(),
            motionLevel = null,
            signalQuality = null,
            rawPayload = payload,
        )
    }

    private fun decodeBinary(identity: BraceletIdentity, payload: ByteArray): BiometricMeasurement {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val heartRate = if (payload.size >= 2) buffer.short.toInt().and(0xFFFF) else null
        val spo2 = if (payload.size >= 3) payload[2].toUByte().toDouble() else null
        val steps = if (payload.size >= 7) buffer.int.toLong().and(0xFFFFFFFFL) else 0L
        val signal = if (payload.size >= 8) payload[7].toUByte().toInt() else null

        return BiometricMeasurement(
            deviceUid = identity.deviceUid,
            serialNumber = identity.serialNumber,
            deviceName = identity.deviceName,
            macAddress = identity.macAddress,
            capturedAt = Instant.now(),
            heartRateBpm = heartRate,
            spo2Percent = spo2,
            stepCount = steps,
            motionLevel = null,
            signalQuality = signal,
            rawPayload = payload,
        )
    }

    private fun parseInstant(value: String?): Instant {
        if (value.isNullOrBlank()) {
            return Instant.now()
        }

        return runCatching { Instant.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).toInstant() }
            .getOrElse { Instant.now() }
    }
}

data class BraceletIdentity(
    val deviceUid: String,
    val serialNumber: String,
    val deviceName: String,
    val macAddress: String,
) {
    companion object {
        fun fromAndroidDevice(
            macAddress: String,
            deviceName: String,
            serialNumber: String? = null,
            deviceUid: String? = null,
        ): BraceletIdentity {
            val normalizedMac = macAddress.ifBlank { "00:00:00:00:00:00" }
            val resolvedSerial = serialNumber ?: normalizedMac.replace(":", "")
            val resolvedDeviceUid = deviceUid ?: UUID.nameUUIDFromBytes(normalizedMac.toByteArray()).toString()
            return BraceletIdentity(
                deviceUid = resolvedDeviceUid,
                serialNumber = resolvedSerial,
                deviceName = deviceName.ifBlank { "Bracelet" },
                macAddress = normalizedMac,
            )
        }
    }
}

fun BiometricMeasurement.toBrokerJson(): String {
    val json = JSONObject()
    json.put("device_uid", deviceUid)
    json.put("serial_number", serialNumber)
    json.put("captured_at", capturedAt.toString())
    json.put("heart_rate_bpm", heartRateBpm)
    json.put("spo2_percent", spo2Percent)
    json.put("step_count", stepCount)
    json.put("motion_level", motionLevel)
    json.put("signal_quality", signalQuality)
    json.put("device_name", deviceName)
    json.put("mac_address", macAddress)
    json.put("raw_payload_base64", Base64.encodeToString(rawPayload, Base64.NO_WRAP))
    return json.toString()
}
