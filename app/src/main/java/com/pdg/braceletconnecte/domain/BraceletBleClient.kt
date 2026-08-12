package com.pdg.braceletconnecte.domain

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import kotlin.math.min

class BraceletBleClient(
    private val context: Context,
    private val config: BraceletBleConfig,
) {

    @SuppressLint("MissingPermission")
    fun observe(): Flow<BraceletEvent> = callbackFlow {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            trySend(BraceletEvent.Error("Ce périphérique ne supporte pas le BLE."))
            close()
            return@callbackFlow
        }

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager?.adapter
            ?: run {
                trySend(BraceletEvent.Error("Bluetooth indisponible sur cet appareil."))
                close()
                return@callbackFlow
            }

        if (!bluetoothAdapter.isEnabled) {
            trySend(BraceletEvent.Error("Bluetooth est désactivé."))
            close()
            return@callbackFlow
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
            ?: run {
                trySend(BraceletEvent.Error("Le scanner BLE est indisponible."))
                close()
                return@callbackFlow
            }

        var currentGatt: BluetoothGatt? = null

        fun emitState(state: ConnectionState) {
            trySend(BraceletEvent.StateChanged(state))
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        emitState(ConnectionState.Connecting)
                        trySend(BraceletEvent.Log("Services en cours de découverte..."))
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        trySend(BraceletEvent.Log("Bracelet déconnecté."))
                        emitState(ConnectionState.Stopped)
                        close()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val characteristic = findNotificationCharacteristic(gatt)
                if (characteristic == null) {
                    trySend(BraceletEvent.Error("Aucune caractéristique de notification trouvée."))
                    emitState(ConnectionState.Error)
                    close()
                    return
                }

                trySend(BraceletEvent.Log("Notifications activées sur ${characteristic.uuid}."))
                enableNotifications(gatt, characteristic)
                emitState(ConnectionState.Connected)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                handlePayload(gatt.device, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handlePayload(gatt.device, value)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    emitState(ConnectionState.Connected)
                }
            }

            private fun handlePayload(device: BluetoothDevice, payload: ByteArray) {
                val identity = BraceletIdentity.fromAndroidDevice(
                    macAddress = device.address,
                    deviceName = device.name ?: config.targetNameContains,
                    serialNumber = config.serialNumber,
                    deviceUid = config.deviceUid,
                )
                val measurement = BraceletMeasurementCodec.decode(identity, payload)
                trySend(BraceletEvent.MeasurementReceived(measurement))
            }
        }

        fun connectToDevice(device: BluetoothDevice) {
            trySend(BraceletEvent.Log("Connexion à ${device.address}..."))
            emitState(ConnectionState.Connecting)
            currentGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        val directAddress = config.targetDeviceAddress
        if (!directAddress.isNullOrBlank()) {
            runCatching { bluetoothAdapter.getRemoteDevice(directAddress) }
                .onSuccess { connectToDevice(it) }
                .onFailure { throwable ->
                    trySend(BraceletEvent.Error("Adresse BLE invalide: $directAddress", throwable))
                    close(throwable)
                }
        } else {
            val filters = mutableListOf<ScanFilter>()
            config.serviceUuid?.let { serviceUuid ->
                filters += ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(serviceUuid))
                    .build()
            }

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val name = device.name.orEmpty()
                    val matchesName = name.contains(config.targetNameContains, ignoreCase = true)
                    val matchesAddress = config.targetDeviceAddress.isNullOrBlank() || device.address.equals(config.targetDeviceAddress, ignoreCase = true)

                    if (matchesName && matchesAddress) {
                        scanner.stopScan(this)
                        trySend(BraceletEvent.Log("Bracelet trouvé: ${device.address}"))
                        connectToDevice(device)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    trySend(BraceletEvent.Error("Échec du scan BLE: $errorCode"))
                    emitState(ConnectionState.Error)
                    close()
                }
            }

            emitState(ConnectionState.Scanning)
            trySend(BraceletEvent.Log("Scan BLE en cours..."))
            scanner.startScan(filters, scanSettings, scanCallback)

            awaitClose {
                scanner.stopScan(scanCallback)
                currentGatt?.close()
                currentGatt = null
            }
            return@callbackFlow
        }

        awaitClose {
            currentGatt?.close()
            currentGatt = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccDescriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (cccDescriptor != null) {
            cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccDescriptor)
        }
    }

    private fun findNotificationCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val serviceUuid = config.serviceUuid?.let(UUID::fromString)
        val characteristicUuid = config.notifyCharacteristicUuid?.let(UUID::fromString)

        if (serviceUuid != null) {
            val service = gatt.getService(serviceUuid) ?: return null
            if (characteristicUuid != null) {
                return service.getCharacteristic(characteristicUuid)
            }
            return service.characteristics.firstOrNull { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
                ?: service.characteristics.firstOrNull { it.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 }
        }

        gatt.services.forEach { service ->
            if (characteristicUuid != null) {
                service.getCharacteristic(characteristicUuid)?.let { return it }
            }

            service.characteristics.firstOrNull { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }?.let { return it }
            service.characteristics.firstOrNull { it.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 }?.let { return it }
        }

        return null
    }

    companion object {
        private val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
