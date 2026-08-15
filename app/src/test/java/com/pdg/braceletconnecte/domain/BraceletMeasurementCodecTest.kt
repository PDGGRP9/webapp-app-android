package com.pdg.braceletconnecte.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `identity resolution from MAC is deterministic`() {
        val first = BraceletIdentity.fromAndroidDevice(macAddress = "AA:BB:CC:DD:EE:FF", deviceName = "BRASCO-00")
        val second = BraceletIdentity.fromAndroidDevice(macAddress = "AA:BB:CC:DD:EE:FF", deviceName = "BRASCO-00")

        assertEquals(first.deviceUid, second.deviceUid)
        assertEquals("AABBCCDDEEFF", first.serialNumber)
    }
}
