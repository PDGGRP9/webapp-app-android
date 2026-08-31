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
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID

/**
 * The BLE link to the bracelet, from scanning to the real-time stream.
 *
 * Flow of a session (see the sequence diagram in the firmware README):
 *   1. scan -> connect -> service discovery
 *   2. subscribe to DATA (live) and HISTORY notifications
 *   3. write the time (TIME) then START on SYNC_CTRL
 *   4. for every history packet: store it locally, THEN ACK
 *   5. "stock empty" packet -> switch to live
 *
 * Two points carry the whole robustness:
 *  - **We only acknowledge after the local write.** As long as the ACK has not
 *    been sent, the bracelet keeps its measurements: a drop loses nothing.
 *  - **A disconnection no longer ends the flow**: we retry, then go back to
 *    scanning. This is the "Reconnection" loop of the README, which absorbs
 *    every cause of a drop without handling them separately.
 *
 * @param onMeasurements stores the measurements and returns how many were
 *   actually new (duplicates are silently ignored).
 */
class BraceletBleClient(
    private val context: Context,
    private val config: BraceletBleConfig,
    private val onMeasurements: suspend (List<BiometricMeasurement>) -> Int = { it.size },
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
        var currentDevice: BluetoothDevice? = null
        var braceletIdentity: BraceletIdentity? = null
        var attempt = 0
        var closing = false
        // Assigned further down: Kotlin does not allow calling a local function
        // declared later, and reconnection needs both.
        var restartScan: () -> Unit = {}
        var reconnect: (BluetoothDevice) -> Unit = {}

        // Counters for the status line: "the app received 60 measurements and
        // ignored 2 because it already knew them".
        var received = 0
        var deduped = 0
        var packets = 0

        // Sync watchdog: the op queue below only watches a single GATT call, not
        // the protocol itself. If START is written and no history packet ever
        // comes back, nothing used to wake the app up — it just waited.
        var currentState: ConnectionState = ConnectionState.Idle
        var syncCtrlChar: BluetoothGattCharacteristic? = null
        var lastHistoryAt = 0L     // 0 = not syncing, nothing to watch
        var startRetries = 0

        // Every GATT operation carries a label: it is what shows up in the status
        // line, so we always know what we are waiting for.
        val ops = ArrayDeque<Pair<String, () -> Unit>>()
        var opRunning: String? = null
        var opStartedAt = 0L
        val gattHandler = Handler(Looper.getMainLooper())

        // Log for debugging
        fun logI(tag: String, message: String) = BleLog.i(tag, message)

        // Log for debugging
        fun logW(tag: String, message: String) = BleLog.w(tag, message)

        // Log for debugging
        /** Counterpart of the firmware's [STATE], mirrored in logcat. */
        fun logState(state: ConnectionState) {
            BleLog.i(
                "STATE",
                "state=$state conn=${if (currentGatt != null) 1 else 0} essai=$attempt " +
                    "attend=${opRunning ?: "-"} paquets=$packets recu=$received dedup=$deduped",
            )
        }

        fun emitState(state: ConnectionState) {
            currentState = state
            trySend(BraceletEvent.StateChanged(state))
            logState(state)
        }

        // --- GATT operation queue ---------------------------------------------
        // Android only accepts one GATT operation at a time: launching two
        // silently drops the second. We serialize them, and each completion
        // callback triggers the next one.
        fun runNextLocked() {
            val op = ops.pollFirst() ?: return
            opRunning = op.first
            opStartedAt = System.currentTimeMillis()
            // A GATT operation must never be started from Android's callback
            // thread: the stack accepts it, sometimes runs it, but no longer
            // reports the answer back — and everything after that is swallowed.
            // So we go through the main thread, with a short delay to let the
            // previous one finish on the stack side.
            gattHandler.postDelayed({ op.second() }, OP_DISPATCH_DELAY_MS)
        }

        fun enqueue(label: String, op: () -> Unit) {
            synchronized(ops) {
                ops.addLast(label to op)
                if (opRunning == null) runNextLocked()
            }
        }

        fun opDone() {
            synchronized(ops) {
                opRunning = null
                runNextLocked()
            }
        }

        fun clearOps() {
            synchronized(ops) {
                ops.clear()
                opRunning = null
            }
            gattHandler.removeCallbacksAndMessages(null)
        }

        // Watchdog: Android sometimes loses a GATT callback. Without this, a
        // single missing answer silently freezes the whole protocol.
        launch {
            while (isActive) {
                delay(1_000)
                val stuck = synchronized(ops) {
                    val label = opRunning
                    if (label != null && System.currentTimeMillis() - opStartedAt > OP_TIMEOUT_MS) label else null
                }
                if (stuck != null) {
                    logW("BLE", "pas de réponse GATT pour « $stuck » depuis ${OP_TIMEOUT_MS}ms -> on débloque la file")
                    opDone()
                }
            }
        }

        /**
         * true if the write actually started. Otherwise no `onCharacteristicWrite`
         * will arrive and the caller must release the queue itself — which is
         * exactly what used to block the whole protocol.
         */
        fun writeTo(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 0 = BluetoothStatusCodes.SUCCESS (API 33)
                gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
            if (!started) {
                logW("BLE", "écriture sur ${characteristic.uuid} refusée par la pile Android")
            }
            return started
        }

        /**
         * The user turned Bluetooth off in the Android settings. Retrying is
         * pointless: `connectGatt` returns null and no callback ever comes back,
         * so the session would hang on "Reconnecting" forever. We end it instead,
         * and the user presses "Démarrer" again once Bluetooth is back.
         *
         * Nothing is lost: the bracelet flushes only after an ACK, so the backlog
         * comes back on the next connection.
         *
         * @return true if Bluetooth is off and the session was ended.
         */
        fun abortIfBluetoothOff(): Boolean {
            if (bluetoothAdapter.isEnabled) return false
            clearOps()
            currentGatt?.close()
            currentGatt = null
            currentDevice = null
            trySend(BraceletEvent.Error("Bluetooth désactivé — réactivez-le puis appuyez sur Démarrer."))
            emitState(ConnectionState.Error)
            close()
            return true
        }

        /**
         * Link lost: we retry, then go back to scanning. The bracelet has flushed
         * nothing — the sync will resume where it stopped and already known
         * measurements will be deduplicated on insert.
         */
        fun handleLostLink(status: Int) {
            clearOps()
            currentGatt?.close()
            currentGatt = null
            if (closing) return
            // Bluetooth off is the one drop we cannot retry through.
            if (abortIfBluetoothOff()) return

            attempt++
            if (attempt <= config.reconnectAttempts) {
                emitState(ConnectionState.Reconnecting)
                logW("BLE", "lien perdu (status=$status) -> tentative $attempt/${config.reconnectAttempts} dans ${config.reconnectDelayMs}ms")
                launch {
                    delay(config.reconnectDelayMs)
                    val device = currentDevice
                    if (!closing && device != null) reconnect(device)
                }
            } else {
                logW("BLE", "${config.reconnectAttempts} tentatives échouées -> retour au scan")
                attempt = 0
                currentDevice = null
                restartScan()
            }
        }

        // Protocol watchdog: START written, no history packet coming back. The
        // bracelet resends its packet on its own after SYNC_ACK_TIMEOUT (5 s), so
        // we wait longer than that before stepping in, otherwise both sides would
        // retry at once. Resending START is harmless: the bracelet flushes
        // nothing that has not been acknowledged.
        launch {
            while (isActive) {
                delay(1_000)
                if (closing || currentState != ConnectionState.Syncing) continue
                if (lastHistoryAt == 0L || System.currentTimeMillis() - lastHistoryAt <= SYNC_STALL_MS) continue

                val gatt = currentGatt
                val ctrl = syncCtrlChar
                if (gatt == null || ctrl == null) continue

                if (startRetries < MAX_START_RETRIES) {
                    startRetries++
                    lastHistoryAt = System.currentTimeMillis()
                    logW("SYNC", "aucun paquet depuis ${SYNC_STALL_MS}ms -> renvoi de START (essai $startRetries/$MAX_START_RETRIES)")
                    enqueue("renvoi START") { if (!writeTo(gatt, ctrl, BraceletMeasurementCodec.CMD_START)) opDone() }
                } else {
                    logW("SYNC", "toujours rien après $MAX_START_RETRIES renvois de START -> on repart sur une reconnexion")
                    lastHistoryAt = 0L
                    startRetries = 0
                    handleLostLink(BluetoothGatt.GATT_FAILURE)
                }
            }
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            // The infamous GATT 133 comes through here: without
                            // this check we would stay stuck on a dead connection.
                            logW("BLE", "connexion en erreur (status=$status)")
                            handleLostLink(status)
                            return
                        }
                        attempt = 0
                        emitState(ConnectionState.Connecting)
                        // The default MTU is 23 bytes, i.e. 20 usable: a history
                        // packet (162 B) would be silently truncated. It is up to
                        // the central to negotiate, the bracelet can only accept.
                        // So we do it before anything else.
                        gattHandler.post {
                            if (!gatt.requestMtu(WANTED_MTU)) {
                                logW("BLE", "négociation du MTU refusée -> découverte directe (paquets limités à 20 octets)")
                                logI("BLE", "découverte des services en cours")
                                gatt.discoverServices()
                            }
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        logI("BLE", "bracelet déconnecté")
                        // We no longer close the flow: we retry, then rescan.
                        handleLostLink(status)
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                // mtu - 3 bytes of ATT header = what actually fits in a
                // notification. A full packet needs 162.
                val usable = mtu - 3
                logI("BLE", "MTU négocié = $mtu octets ($usable utiles, il en faut 162 pour un paquet plein)")
                gattHandler.post {
                    logI("BLE", "découverte des services en cours")
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    logW("BLE", "découverte des services en échec (status=$status)")
                    handleLostLink(status)
                    return
                }

                val characteristic = findNotificationCharacteristic(gatt)
                if (characteristic == null) {
                    trySend(BraceletEvent.Error("Aucune caractéristique de notification trouvée."))
                    emitState(ConnectionState.Error)
                    close()
                    return
                }

                // The three catch-up characteristics. An older firmware does not
                // have them: we then just listen to the live stream.
                val service = gatt.getService(UUID.fromString(config.serviceUuid))
                val history = service?.getCharacteristic(UUID.fromString(config.historyCharacteristicUuid))
                val syncCtrl = service?.getCharacteristic(UUID.fromString(config.syncCtrlCharacteristicUuid))
                val time = service?.getCharacteristic(UUID.fromString(config.timeCharacteristicUuid))

                logI("BLE", "notifications activées sur ${characteristic.uuid}")
                // We log the declared properties: a characteristic without the W
                // bit is refused for writing by Android without an explicit error,
                // and the protocol freezes. Better to see it right away.
                logI(
                    "BLE",
                    "propriétés : live=${describeProps(characteristic)} history=${describeProps(history)} " +
                        "sync=${describeProps(syncCtrl)} time=${describeProps(time)}",
                )
                emitState(ConnectionState.Connected)

                // Order matters: subscribe first, otherwise the bracelet's first
                // notifications go nowhere.
                enqueue("abonnement live") { if (!enableNotifications(gatt, characteristic)) opDone() }

                if (history == null || syncCtrl == null || time == null) {
                    logW("BLE", "firmware sans rattrapage de backlog : écoute du direct uniquement")
                    return
                }

                // Kept for the watchdog and for the STOP sent when the user leaves.
                syncCtrlChar = syncCtrl

                enqueue("abonnement history") { if (!enableNotifications(gatt, history)) opDone() }
                enqueue("écriture TIME") {
                    val epoch = Instant.now().epochSecond
                    logI("SYNC", "envoi de l'heure au bracelet (epoch=$epoch)")
                    if (!writeTo(gatt, time, BraceletMeasurementCodec.encodeEpoch(epoch))) opDone()
                }
                enqueue("écriture START") {
                    logI("SYNC", "START -> demande du backlog")
                    // Arms the watchdog: from here on we expect a history packet.
                    lastHistoryAt = System.currentTimeMillis()
                    startRetries = 0
                    emitState(ConnectionState.Syncing)
                    if (!writeTo(gatt, syncCtrl, BraceletMeasurementCodec.CMD_START)) opDone()
                }
            }

            // Android < 13 path: the value is carried by the characteristic.
            @Deprecated("Replaced by the overload taking `value` since Android 13")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                dispatch(gatt, characteristic, characteristic.value ?: ByteArray(0))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                dispatch(gatt, characteristic, value)
            }

            private fun dispatch(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid.toString().equals(config.historyCharacteristicUuid, ignoreCase = true)) {
                    handleHistory(gatt, value)
                } else {
                    handlePayload(gatt.device, value)
                }
            }

            /**
             * A history packet. The ACK is only sent once the measurements are
             * stored: that is the invariant of the protocol. If the app is killed
             * here, the bracelet still has its stock and will resend the packet.
             */
            private fun handleHistory(gatt: BluetoothGatt, payload: ByteArray) {
                // The bracelet is answering: the watchdog restarts from zero, even
                // for a truncated packet — the link is alive, that is what matters.
                lastHistoryAt = System.currentTimeMillis()
                startRetries = 0
                val id = braceletIdentity ?: return
                when (val packet = BraceletMeasurementCodec.decodeHistoryPacket(payload)) {
                    is BraceletMeasurementCodec.HistoryPacket.End -> {
                        logI("SYNC", "le bracelet annonce son stock vide -> passage en direct")
                        emitState(ConnectionState.Live)
                    }

                    is BraceletMeasurementCodec.HistoryPacket.Invalid -> {
                        // No ACK: the bracelet will resend after its timeout, and
                        // nothing will have been flushed in the meantime.
                        logW("SYNC", "paquet illisible (${packet.reason}) -> pas d'ACK, le bracelet renverra")
                    }

                    is BraceletMeasurementCodec.HistoryPacket.Data -> {
                        packets++
                        // The bracelet can start a new flush while we were already
                        // live: the screen must say so.
                        emitState(ConnectionState.Syncing)

                        // Records taken before the bracelet had the time carry an uptime it
                        // could not resolve (previous boot cycle): they fall back on the
                        // reception time. Spacing them by READ_INTERVAL keeps their `ts`
                        // distinct — otherwise the (deviceUid, ts) key keeps only one of
                        // them and we would ACK away the rest.
                        val now = Instant.now()
                        val measurements = packet.records.mapIndexed { i, record ->
                            BraceletMeasurementCodec.toMeasurement(
                                id,
                                record,
                                now.minusSeconds(4L * (packet.records.size - 1 - i)),
                            )
                        }
                        received += measurements.size
                        measurements.lastOrNull()?.let { trySend(BraceletEvent.MeasurementReceived(it)) }

                        launch {
                            val inserted = runCatching { onMeasurements(measurements) }.getOrElse { error ->
                                logW("SYNC", "enregistrement local impossible (${error.message}) -> pas d'ACK")
                                return@launch
                            }
                            val ignored = measurements.size - inserted
                            deduped += ignored
                            logI(
                                "SYNC",
                                "Paquet #${packet.seq} reçu (${measurements.size} mesures) -> inséré $inserted / " +
                                    "ignoré $ignored (déjà vus) -> ACK",
                            )
                            val syncCtrl = gatt.getService(UUID.fromString(config.serviceUuid))
                                ?.getCharacteristic(UUID.fromString(config.syncCtrlCharacteristicUuid))
                            if (syncCtrl == null) {
                                logW("SYNC", "SYNC_CTRL introuvable : impossible d'acquitter")
                                return@launch
                            }
                            // The ACK echoes the packet's sequence number: the bracelet
                            // refuses any mismatch instead of flushing the wrong batch.
                            val ack = BraceletMeasurementCodec.ackFor(packet.seq)
                            enqueue("écriture ACK #${packet.seq}") { if (!writeTo(gatt, syncCtrl, ack)) opDone() }
                        }
                    }
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    logW("BLE", "abonnement refusé sur ${descriptor.characteristic.uuid} (status=$status)")
                }
                opDone()
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    logW("SYNC", "écriture sur ${characteristic.uuid} refusée (status=$status)")
                }
                opDone()
            }

            private fun handlePayload(device: BluetoothDevice, payload: ByteArray) {
                val identity = BraceletIdentity.fromAndroidDevice(
                    macAddress = device.address,
                    deviceName = device.name ?: config.targetNameContains,
                    serialNumber = config.serialNumber,
                    deviceUid = config.deviceUid,
                )
                val measurement = BraceletMeasurementCodec.decode(identity, payload)
                received++
                trySend(BraceletEvent.MeasurementReceived(measurement))
                // Live data goes through the same local database as history: it is
                // the uploader that talks to the backend, not the BLE link.
                launch { onMeasurements(listOf(measurement)) }
            }
        }

        fun connectToDevice(device: BluetoothDevice) {
            currentDevice = device
            braceletIdentity = BraceletIdentity.fromAndroidDevice(
                macAddress = device.address,
                deviceName = device.name ?: config.targetNameContains,
                serialNumber = config.serialNumber,
                deviceUid = config.deviceUid,
            )
            logI("BLE", "connexion à ${device.address}")
            emitState(ConnectionState.Connecting)
            currentGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            // null = the Android stack refused to open a client (typically the
            // adapter went off in between). No callback will ever come, so we
            // must not just sit there waiting for one.
            if (currentGatt == null && !abortIfBluetoothOff()) {
                trySend(BraceletEvent.Error("Connexion BLE refusée par le système."))
                emitState(ConnectionState.Error)
                close()
            }
        }
        reconnect = ::connectToDevice

        val directAddress = config.targetDeviceAddress
        if (!directAddress.isNullOrBlank()) {
            // No scan in this mode: "back to scanning" = retry the address.
            restartScan = {
                runCatching { bluetoothAdapter.getRemoteDevice(directAddress) }
                    .onSuccess { connectToDevice(it) }
            }
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

            var scanning = false

            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val name = device.name.orEmpty()
                    val matchesName = name.contains(config.targetNameContains, ignoreCase = true)
                    val matchesAddress = config.targetDeviceAddress.isNullOrBlank() || device.address.equals(config.targetDeviceAddress, ignoreCase = true)

                    // Android can deliver several results before stopScan takes
                    // effect: without this guard we open as many GATT connections
                    // as there are callbacks, and writes go out on different
                    // clients.
                    if (currentDevice != null) return

                    if (matchesName && matchesAddress) {
                        runCatching { scanner.stopScan(this) }
                        scanning = false
                        logI("BLE", "bracelet trouvé: ${device.address}")
                        connectToDevice(device)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    scanning = false
                    trySend(BraceletEvent.Error("Échec du scan BLE: $errorCode"))
                    emitState(ConnectionState.Error)
                    close()
                }
            }

            fun startScan() {
                if (scanning || closing) return
                if (abortIfBluetoothOff()) return
                scanning = true
                emitState(ConnectionState.Scanning)
                logI("BLE", "scan BLE en cours")
                // startScan throws IllegalStateException("BT Adapter is not turned
                // ON") on several Android versions, and we are called from a
                // binder thread: an escaping exception would kill the session
                // without a word.
                runCatching { scanner.startScan(filters, scanSettings, scanCallback) }
                    .onFailure { throwable ->
                        scanning = false
                        if (!abortIfBluetoothOff()) {
                            trySend(BraceletEvent.Error("Scan BLE impossible.", throwable))
                            emitState(ConnectionState.Error)
                            close()
                        }
                    }
            }
            restartScan = ::startScan

            startScan()

            awaitClose {
                closing = true
                clearOps()
                // Same as startScan: stopping on an adapter that is already off
                // throws, and awaitClose must never let an exception escape.
                if (scanning) runCatching { scanner.stopScan(scanCallback) }
                val gatt = currentGatt
                currentGatt = null
                val ctrl = syncCtrlChar
                syncCtrlChar = null
                if (gatt != null && ctrl != null) {
                    // Tell the bracelet we are leaving, otherwise it stays in
                    // WAIT_ACK until its own timeout. The close is delayed a
                    // little: closing right away would kill the write before the
                    // Android stack has sent it.
                    BleLog.i("SYNC", "STOP -> le bracelet peut repasser en idle")
                    runCatching { writeTo(gatt, ctrl, BraceletMeasurementCodec.CMD_STOP) }
                    gattHandler.postDelayed({ runCatching { gatt.close() } }, STOP_GRACE_MS)
                } else {
                    gatt?.close()
                }
                // Log for debugging
                BleLog.i("BLE", "session fermée (reçu=$received dédup=$deduped paquets=$packets)")
            }
            return@callbackFlow
        }

        awaitClose {
            closing = true
            clearOps()
            currentGatt?.close()
            currentGatt = null
        }
    }

    /** true if a GATT write was started: otherwise the queue would stay blocked. */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccDescriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (cccDescriptor == null) {
            // Log for debugging
            BleLog.w("BLE", "pas de descripteur CCCD sur ${characteristic.uuid}")
            return false
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccDescriptor, value)
        } else {
            @Suppress("DEPRECATION")
            cccDescriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccDescriptor)
        }
        return true
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

    /** Readable summary of the GATT properties: "RWN", "-W-", "absente". */
    private fun describeProps(characteristic: BluetoothGattCharacteristic?): String {
        if (characteristic == null) return "absente"
        val p = characteristic.properties
        val read = if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) "R" else "-"
        val write = if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) "W" else "-"
        val notify = if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) "N" else "-"
        return "$read$write$notify"
    }

    companion object {
        private val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Past this, the GATT answer is considered lost and the queue is restarted. */
        private const val OP_TIMEOUT_MS = 5_000L

        /** Breathing room between two GATT operations: the Android stack dislikes immediate chaining. */
        private const val OP_DISPATCH_DELAY_MS = 50L

        /** Enough to fit a full history packet (2 + 20*8 = 162 B + 3 B of ATT). */
        private const val WANTED_MTU = 247

        /**
         * Silence after START before we resend it. Longer than the bracelet's own
         * SYNC_ACK_TIMEOUT (5 s) so its resend gets the first word.
         */
        private const val SYNC_STALL_MS = 8_000L

        /** Past this many resends, the link is considered dead: we reconnect. */
        private const val MAX_START_RETRIES = 3

        /** Time left to the Android stack to send STOP before the GATT is closed. */
        private const val STOP_GRACE_MS = 200L
    }
}
