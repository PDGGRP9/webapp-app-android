package com.pdg.braceletconnecte.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Covers the payload format actually sent by firmware/ (branch core-firmware)
 * BLEManager::sendSensorData: [HR uint8][SpO2 uint8][Steps MSB][Steps LSB], verified
 * against a real ESP32 (XIAO ESP32S3, TEST_INTEGRATION mode) on 2026-08-14.
 */
class BraceletMeasurementCodecTest {

    private val identity = BraceletIdentity.fromAndroidDevice(
        macAddress = "AA:BB:CC:DD:EE:FF",
        deviceName = "BRASCO-00",
    )

    @Test
    fun `decodes core-firmware 4-byte payload`() {
        // HR=75, SpO2=98, steps=1234 (0x04D2, big-endian)
        val payload = byteArrayOf(75, 98, 0x04, 0xD2.toByte())

        val measurement = BraceletMeasurementCodec.decode(identity, payload)

        assertEquals(75, measurement.heartRateBpm)
        assertEquals(98.0, measurement.spo2Percent)
        assertEquals(1234L, measurement.stepCount)
    }

    @Test
    fun `zero heart rate and spo2 mean no reading, not a real zero`() {
        val payload = byteArrayOf(0, 0, 0, 0)

        val measurement = BraceletMeasurementCodec.decode(identity, payload)

        assertNull(measurement.heartRateBpm)
        assertNull(measurement.spo2Percent)
        assertEquals(0L, measurement.stepCount)
    }

    @Test
    fun `255 means no reading too, a bracelet not yet reflashed sends it`() {
        // The oximeter driver returns -1 when it has nothing: truncated to
        // uint8_t that gives 255. Posted as is, the backend answered 500 and the
        // whole queue stayed stuck behind that measurement.
        val record = BraceletMeasurementCodec.Record(ts = 1_700_000_000L, hr = 255, spo2 = 255, steps = 42)

        val measurement = BraceletMeasurementCodec.toMeasurement(identity, record)

        assertNull(measurement.heartRateBpm)
        assertNull(measurement.spo2Percent)
        assertEquals(42L, measurement.stepCount)
    }

    // --- 8-byte record and history packets -----------------------------------
    // Mirror of firmware/test/test_native/test_main.cpp: both suites must stay in
    // agreement on the byte order.

    @Test
    fun `decodes the 8-byte little-endian record`() {
        // ts=0x11223344, hr=0xAA, spo2=0xBB, steps=0xCCDD
        val payload = byteArrayOf(0x44, 0x33, 0x22, 0x11, 0xAA.toByte(), 0xBB.toByte(), 0xDD.toByte(), 0xCC.toByte())

        val record = BraceletMeasurementCodec.decodeRecord(payload)

        assertEquals(0x11223344L, record.ts)
        assertEquals(0xAA, record.hr)
        assertEquals(0xBB, record.spo2)
        assertEquals(0xCCDD, record.steps)
    }

    @Test
    fun `an 8-byte payload goes through the new decoder, not the heuristics`() {
        // ts=1755950400, hr=72, spo2=98, steps=1234
        val payload = byteArrayOf(0x00, 0x62.toByte(), 0xA6.toByte(), 0x68, 72, 98, 0xD2.toByte(), 0x04)

        val measurement = BraceletMeasurementCodec.decode(identity, payload)

        assertEquals(72, measurement.heartRateBpm)
        assertEquals(98.0, measurement.spo2Percent)
        assertEquals(1234L, measurement.stepCount)
    }

    @Test
    fun `decodes a full history packet`() {
        val header = BraceletMeasurementCodec.HISTORY_HEADER_SIZE
        val size = BraceletMeasurementCodec.MEASUREMENT_SIZE
        val payload = ByteArray(header + 2 * size)
        payload[0] = BraceletMeasurementCodec.HISTORY_TYPE_DATA.toByte()
        payload[1] = 2
        payload[2] = 0x2A; payload[3] = 0x01                            // seq = 298
        payload[header] = 100; payload[header + 4] = 72; payload[header + 5] = 98
        payload[header + size] = 104; payload[header + size + 4] = 73; payload[header + size + 5] = 97

        val packet = BraceletMeasurementCodec.decodeHistoryPacket(payload)

        assertTrue(packet is BraceletMeasurementCodec.HistoryPacket.Data)
        val data = packet as BraceletMeasurementCodec.HistoryPacket.Data
        assertEquals(2, data.records.size)
        assertEquals(100L, data.records[0].ts)
        assertEquals(72, data.records[0].hr)
        assertEquals(104L, data.records[1].ts)
        // Little-endian, and it is what the ACK has to echo back.
        assertEquals(298, data.seq)
    }

    @Test
    fun `end packet means the bracelet has nothing left`() {
        val payload = byteArrayOf(0xFF.toByte(), 0, 0, 0)

        assertEquals(
            BraceletMeasurementCodec.HistoryPacket.End,
            BraceletMeasurementCodec.decodeHistoryPacket(payload),
        )
    }

    @Test
    fun `a packet from a firmware without sequence numbers is refused`() {
        // Old header was 2 bytes and type 0x01. Decoded with the current layout,
        // every record would be shifted by two bytes and we would ACK garbage.
        val payload = ByteArray(2 + BraceletMeasurementCodec.MEASUREMENT_SIZE)
        payload[0] = 0x01
        payload[1] = 1

        assertTrue(BraceletMeasurementCodec.decodeHistoryPacket(payload) is BraceletMeasurementCodec.HistoryPacket.Invalid)
    }

    @Test
    fun `the ack echoes the sequence number little-endian`() {
        val ack = BraceletMeasurementCodec.ackFor(298)

        assertEquals(3, ack.size)
        assertEquals(0x02.toByte(), ack[0])
        assertEquals(0x2A.toByte(), ack[1])
        assertEquals(0x01.toByte(), ack[2])
    }

    @Test
    fun `a truncated packet is rejected, not half-decoded`() {
        // 20 measurements announced but only one present: no ACK, the bracelet will resend
        val payload = ByteArray(BraceletMeasurementCodec.HISTORY_HEADER_SIZE + BraceletMeasurementCodec.MEASUREMENT_SIZE)
        payload[0] = BraceletMeasurementCodec.HISTORY_TYPE_DATA.toByte()
        payload[1] = 20

        assertTrue(BraceletMeasurementCodec.decodeHistoryPacket(payload) is BraceletMeasurementCodec.HistoryPacket.Invalid)
    }

    @Test
    fun `epoch is encoded little-endian on 4 bytes`() {
        val bytes = BraceletMeasurementCodec.encodeEpoch(0x11223344L)

        assertEquals(4, bytes.size)
        assertEquals(0x44.toByte(), bytes[0])
        assertEquals(0x11.toByte(), bytes[3])
    }

    @Test
    fun `a measurement still carrying an uptime gets the reception time`() {
        // Below TS_EPOCH_MIN, `ts` is the bracelet's uptime, not an epoch: the
        // firmware could not resolve it (previous boot cycle). Taken as an epoch
        // it would land in 1970 and the backend would reject it.
        val receivedAt = Instant.ofEpochSecond(1_700_000_000L)

        val measurement = BraceletMeasurementCodec.toMeasurement(
            identity,
            BraceletMeasurementCodec.Record(ts = 420, hr = 70, spo2 = 96, steps = 5),
            receivedAt,
        )

        assertEquals(receivedAt, measurement.capturedAt)
    }

    @Test
    fun `a resolved epoch is kept as is`() {
        val receivedAt = Instant.ofEpochSecond(1_700_000_000L)
        val takenAt = 1_688_000_000L

        val measurement = BraceletMeasurementCodec.toMeasurement(
            identity,
            BraceletMeasurementCodec.Record(ts = takenAt, hr = 70, spo2 = 96, steps = 5),
            receivedAt,
        )

        assertEquals(Instant.ofEpochSecond(takenAt), measurement.capturedAt)
    }

    @Test
    fun `identity resolution from MAC is deterministic`() {
        val first = BraceletIdentity.fromAndroidDevice(macAddress = "AA:BB:CC:DD:EE:FF", deviceName = "BRASCO-00")
        val second = BraceletIdentity.fromAndroidDevice(macAddress = "AA:BB:CC:DD:EE:FF", deviceName = "BRASCO-00")

        assertEquals(first.deviceUid, second.deviceUid)
        assertEquals("AABBCCDDEEFF", first.serialNumber)
    }
}
