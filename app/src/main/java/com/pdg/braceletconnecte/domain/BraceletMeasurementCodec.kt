package com.pdg.braceletconnecte.domain

import android.util.Base64
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

object BraceletMeasurementCodec {

    // --- Sync protocol (firmware/src/logic/measurement.h) ----------------------
    // The firmware now sends the SAME 8-byte record both live and in history. The
    // older decoders below stay in place for firmwares that are not updated yet.
    const val MEASUREMENT_SIZE = 8

    /** `[type][count][seqLo][seqHi]`, then the records. */
    const val HISTORY_HEADER_SIZE = 4

    /**
     * 0x11, not 0x01: a firmware from before the sequence number used a 2-byte
     * header. Reading such a packet with this decoder would shift every record
     * by two bytes and we would ACK garbage. A type we do not know is refused
     * instead, and shows up in the logs.
     */
    const val HISTORY_TYPE_DATA = 0x11
    const val HISTORY_TYPE_END = 0xFF

    /**
     * Below this, `ts` is not an epoch but the bracelet's uptime in seconds: the
     * measurement was taken before the app had given it the time. A real epoch is
     * always above (Sept. 2020), an uptime never reaches it. Same rule on the
     * firmware side (`src/logic/measurement.h`, TS_EPOCH_MIN).
     */
    const val TS_EPOCH_MIN = 1_600_000_000L

    /**
     * Ranges accepted as a "real reading". Same bounds as `sanitizeReading()` on
     * the firmware side (`src/logic/measurement.h`): outside of them it is noise
     * or the sensor's -1, hence `null` and not a value.
     */
    val PLAUSIBLE_HR = 1..250
    val PLAUSIBLE_SPO2 = 1..100

    /** Commands written to SYNC_CTRL. START and STOP are a single byte. */
    val CMD_START = byteArrayOf(0x01)
    val CMD_STOP = byteArrayOf(0x03)

    /**
     * The ACK carries the sequence number of the packet it acknowledges:
     * `[0x02][seqLo][seqHi]`. Without it a late or duplicated ACK would be taken
     * for the current packet's and the bracelet would flush measurements we never
     * received (firmware `reports/diagnostic-sync-ble-2026-08-31.md`).
     */
    fun ackFor(seq: Int): ByteArray = byteArrayOf(
        0x02,
        (seq and 0xFF).toByte(),
        ((seq shr 8) and 0xFF).toByte(),
    )

    /** A measurement as it comes out of the bracelet, before interpretation. */
    data class Record(
        val ts: Long,   // epoch UTC in seconds; 0 = the bracelet had no clock yet
        val hr: Int,    // 0 = no reading
        val spo2: Int,  // 0 = no reading
        val steps: Int,
    )

    /** Result of decoding a HISTORY packet. */
    sealed interface HistoryPacket {
        /** `seq` is echoed back in the ACK. */
        data class Data(val records: List<Record>, val seq: Int) : HistoryPacket
        /** The bracelet has nothing left in stock: we can switch to live. */
        data object End : HistoryPacket
        data class Invalid(val reason: String) : HistoryPacket
    }

    fun decode(identity: BraceletIdentity, payload: ByteArray): BiometricMeasurement {
        decodeJson(identity, payload)?.let { return it }
        // The up-to-date firmware sends a fixed 8-byte record. As for the 4-byte
        // case below, we check the size before the text heuristic, which could
        // otherwise fire if a byte happens to equal ',' / ';' / '|'.
        if (payload.size == MEASUREMENT_SIZE) {
            return toMeasurement(identity, decodeRecord(payload))
        }
        // firmware/ (branch core-firmware) always sends a fixed 4-byte struct: check this
        // deterministically before the delimited-text heuristic, which could otherwise
        // misfire if a raw byte happens to equal ',' / ';' / '|'.
        if (payload.size == 4) {
            return decodeCoreFirmwareBinary(identity, payload)
        }
        decodeDelimitedText(identity, payload)?.let { return it }
        return decodeBinary(identity, payload)
    }

    /**
     * The 8-byte record, strict little-endian. Exact counterpart of
     * `encodeMeasurement()` on the firmware side: if the two diverge, the app
     * shows wrong values without anything visibly breaking.
     */
    fun decodeRecord(payload: ByteArray, offset: Int = 0): Record {
        fun byte(i: Int) = payload[offset + i].toInt() and 0xFF
        return Record(
            ts = byte(0).toLong() or (byte(1).toLong() shl 8) or
                (byte(2).toLong() shl 16) or (byte(3).toLong() shl 24),
            hr = byte(4),
            spo2 = byte(5),
            steps = byte(6) or (byte(7) shl 8),
        )
    }

    fun decodeHistoryPacket(payload: ByteArray): HistoryPacket {
        if (payload.size < HISTORY_HEADER_SIZE) {
            return HistoryPacket.Invalid("paquet trop court (${payload.size} octets)")
        }
        val type = payload[0].toInt() and 0xFF
        val count = payload[1].toInt() and 0xFF

        if (type == HISTORY_TYPE_END) return HistoryPacket.End
        if (type != HISTORY_TYPE_DATA) {
            return HistoryPacket.Invalid("type de paquet inconnu 0x%02X".format(type))
        }

        val expected = HISTORY_HEADER_SIZE + count * MEASUREMENT_SIZE
        if (payload.size < expected) {
            return HistoryPacket.Invalid(
                "paquet tronqué : $count mesures annoncées, ${payload.size} octets reçus " +
                    "(il en faudrait $expected)",
            )
        }

        val seq = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
        return HistoryPacket.Data(
            (0 until count).map { decodeRecord(payload, HISTORY_HEADER_SIZE + it * MEASUREMENT_SIZE) },
            seq,
        )
    }

    /** UTC epoch as 4 little-endian bytes, for the TIME characteristic. */
    fun encodeEpoch(epochSeconds: Long): ByteArray = byteArrayOf(
        (epochSeconds and 0xFF).toByte(),
        ((epochSeconds shr 8) and 0xFF).toByte(),
        ((epochSeconds shr 16) and 0xFF).toByte(),
        ((epochSeconds shr 24) and 0xFF).toByte(),
    )

    /**
     * The same rules as for the 4-byte decoder below:
     *  - `ts = 0` = the bracelet had not received the time yet. We give it the
     *    reception time, otherwise all those measurements would share the same
     *    timestamp and deduplication would keep only one.
     *  - `hr`/`spo2` at 0 = "no reading" (no finger), not a real 0.
     *  - a value outside the physiological range = no reading either. A bracelet
     *    that has not been reflashed sends 255 (the oximeter driver's -1
     *    truncated to uint8_t): posted as is, the backend rejected it with a 500
     *    and the whole queue stayed stuck behind it.
     */
    fun toMeasurement(
        identity: BraceletIdentity,
        record: Record,
        receivedAt: Instant = Instant.now(),
    ): BiometricMeasurement = BiometricMeasurement(
        deviceUid = identity.deviceUid,
        serialNumber = identity.serialNumber,
        deviceName = identity.deviceName,
        macAddress = identity.macAddress,
        capturedAt = if (record.ts >= TS_EPOCH_MIN) Instant.ofEpochSecond(record.ts) else receivedAt,
        heartRateBpm = record.hr.takeIf { it in PLAUSIBLE_HR },
        spo2Percent = record.spo2.takeIf { it in PLAUSIBLE_SPO2 }?.toDouble(),
        stepCount = record.steps.toLong(),
        rawPayload = ByteArray(MEASUREMENT_SIZE),
    )

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
            heartRateBpm = heartRate.takeIf { it in PLAUSIBLE_HR },
            spo2Percent = spo2.takeIf { it in PLAUSIBLE_SPO2 }?.toDouble(),
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
