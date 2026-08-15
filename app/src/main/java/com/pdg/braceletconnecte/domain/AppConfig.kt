package com.pdg.braceletconnecte.domain

data class AppConfig(
    val bracelet: BraceletBleConfig = BraceletBleConfig(),
) {
    companion object {
        fun default(): AppConfig = AppConfig()
    }
}

data class BraceletBleConfig(
    // Matches firmware/ (branch core-firmware) include/config.h: BLE_DEVICE_NAME, SERVICE_UUID, DATA_UUID.
    val targetNameContains: String = "BRASCO",
    val targetDeviceAddress: String? = null,
    val serialNumber: String? = null,
    val deviceUid: String? = null,
    val serviceUuid: String? = "146ef449-0083-438a-9af6-5be5bb541e2c",
    val notifyCharacteristicUuid: String? = "146ef450-0083-438a-9af6-5be5bb541e2c",
)

/** Default backend base URL used for the emulator (10.0.2.2 routes to the host machine). */
const val DEFAULT_WEBAPP_BASE_URL = "http://10.0.2.2:8000"
