package com.pdg.braceletconnecte.domain

data class AppConfig(
    val bracelet: BraceletBleConfig = BraceletBleConfig(),
) {
    companion object {
        fun default(): AppConfig = AppConfig()
    }
}

data class BraceletBleConfig(
    val targetNameContains: String = "bracelet",
    val targetDeviceAddress: String? = null,
    val serialNumber: String? = null,
    val deviceUid: String? = null,
    val serviceUuid: String? = null,
    val notifyCharacteristicUuid: String? = null,
)

/** Default backend base URL used for the emulator (10.0.2.2 routes to the host machine). */
const val DEFAULT_WEBAPP_BASE_URL = "http://10.0.2.2:8000"
