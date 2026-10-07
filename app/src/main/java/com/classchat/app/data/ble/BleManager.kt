package com.classchat.app.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.classchat.app.model.BlePacket
import com.classchat.app.model.PeerUser
import com.classchat.app.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BleDiagnostics(
    val isBluetoothEnabled: Boolean = false,
    val isAdvertising: Boolean = false,
    val isScanning: Boolean = false,
    val discoveredPeerCount: Int = 0,
    val advertisingError: String? = null
)

class BleManager(
    private val context: Context,
    private val onMessageReceived: (BlePacket) -> Unit,
    private val onAckReceived: (String) -> Unit
) {
    private val tag = "ClassChat_BleManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val advertiser = BleAdvertiser(context, bluetoothAdapter)
    private val scanner = BleScanner(context, bluetoothAdapter, scope)
    private val client = BleGattClient(context, bluetoothAdapter)
    private val dispatcher = BleQueueDispatcher(client, scope)
    private val meshRelay = MeshRelayEngine(dispatcher)

    private var gattServer: BleGattServer? = null
    private var currentUserProfile: UserProfile? = null

    val discoveredPeers: StateFlow<List<PeerUser>> = scanner.discoveredPeers

    private val _diagnostics = MutableStateFlow(BleDiagnostics())
    val diagnostics: StateFlow<BleDiagnostics> = _diagnostics.asStateFlow()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val isEnabled = (state == BluetoothAdapter.STATE_ON)
                Log.i(tag, "Bluetooth state changed: isEnabled=$isEnabled")
                updateDiagnostics()

                if (isEnabled && currentUserProfile != null) {
                    startServices(currentUserProfile!!)
                } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    stopServices()
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(bluetoothStateReceiver, filter)
        }
        updateDiagnostics()

        // Sync advertiser/scanner state with diagnostics
        scope.launch {
            advertiser.isAdvertising.collect { updateDiagnostics() }
        }
        scope.launch {
            advertiser.advertisingError.collect { updateDiagnostics() }
        }
        scope.launch {
            scanner.discoveredPeers.collect { updateDiagnostics() }
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun startServices(profile: UserProfile) {
        currentUserProfile = profile
        if (!isBluetoothEnabled()) {
            Log.w(tag, "Cannot start services: Bluetooth is disabled")
            updateDiagnostics()
            return
        }

        Log.i(tag, "Starting ClassChat BLE services for ${profile.displayName}...")

        // 1. Start GATT Server to receive incoming packets
        if (gattServer == null) {
            gattServer = BleGattServer(context, bluetoothManager) { packet, senderAddress ->
                handleIncomingPacket(packet, senderAddress)
            }
        }
        gattServer?.start(profile)

        // 2. Start Advertising presence beacon
        advertiser.startAdvertising(profile)

        // 3. Start Scanning for classroom peers
        scanner.startScanning(profile.userId)

        updateDiagnostics()
    }

    fun stopServices() {
        Log.i(tag, "Stopping ClassChat BLE services...")
        advertiser.stopAdvertising()
        scanner.stopScanning()
        gattServer?.stop()
        gattServer = null
        updateDiagnostics()
    }

    fun updateProfile(profile: UserProfile) {
        currentUserProfile = profile
        gattServer?.updateProfile(profile)
        if (advertiser.isAdvertising.value) {
            advertiser.startAdvertising(profile)
        }
    }

    private fun handleIncomingPacket(packet: BlePacket, senderAddress: String) {
        // Prevent duplicate packet processing
        if (meshRelay.isDuplicate(packet.messageId)) {
            Log.d(tag, "Dropping duplicate packet ${packet.messageId}")
            return
        }

        // Register peer address and presence immediately so receiver can reply without discovering first
        if (senderAddress.isNotEmpty() && senderAddress != "unknown") {
            scanner.recordPeerPresence(packet.senderId, packet.senderName, senderAddress)
        }

        when (packet.type) {
            BlePacket.TYPE_DIRECT_MESSAGE -> {
                // Check if directed to me
                val myId = currentUserProfile?.userId ?: ""
                val myHash = myId.hashCode()

                if (packet.recipientId.equals(myId, ignoreCase = true) ||
                    packet.recipientId.equals("hash_$myHash", ignoreCase = true)) {
                    Log.i(tag, "Direct message received for me from ${packet.senderName}")
                    onMessageReceived(packet)

                    // Send ACK back to sender
                    sendAck(packet.messageId, packet.senderId, senderAddress)
                } else {
                    Log.d(tag, "Direct message for someone else (${packet.recipientId}), ignoring")
                }
            }

            BlePacket.TYPE_GROUP_MESSAGE -> {
                Log.i(tag, "Class group message received from ${packet.senderName}: '${packet.content}'")
                onMessageReceived(packet)

                // Relay to other nearby peers in the classroom mesh
                currentUserProfile?.let { me ->
                    meshRelay.relayGroupBroadcast(packet, me.userId, scanner.discoveredPeers.value)
                }
            }

            BlePacket.TYPE_ACK -> {
                Log.i(tag, "ACK received for message ${packet.messageId}")
                onAckReceived(packet.messageId)
            }
        }
    }

    private fun sendAck(messageId: String, senderId: String, senderAddress: String) {
        val myId = currentUserProfile?.userId ?: ""
        val ackPacket = BlePacket.createAck(messageId, myId, senderId)
        val payload = ackPacket.toByteArray()

        dispatcher.enqueue(
            OutgoingTask(
                messageId = messageId,
                deviceAddress = senderAddress,
                payload = payload,
                onResult = { success ->
                    Log.d(tag, "ACK transmission for $messageId to $senderAddress: $success")
                }
            )
        )
    }

    fun sendDirectMessage(
        messageId: String,
        targetUserId: String,
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        val profile = currentUserProfile
        if (profile == null) {
            onResult(false)
            return
        }

        val deviceAddress = scanner.getDeviceAddressForPeer(targetUserId)
        if (deviceAddress == null) {
            Log.w(tag, "Device address not found in scanner cache for $targetUserId")
            onResult(false)
            return
        }

        val packet = BlePacket.createDirectMessage(
            messageId = messageId,
            senderId = profile.userId,
            senderName = profile.displayName,
            recipientId = targetUserId,
            text = text
        )

        meshRelay.markSeen(messageId)

        dispatcher.enqueue(
            OutgoingTask(
                messageId = messageId,
                deviceAddress = deviceAddress,
                payload = packet.toByteArray(),
                onResult = onResult
            )
        )
    }

    fun sendGroupMessage(
        messageId: String,
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        val profile = currentUserProfile
        if (profile == null) {
            onResult(false)
            return
        }

        val peers = scanner.discoveredPeers.value.filter { it.isOnline }
        if (peers.isEmpty()) {
            Log.w(tag, "No online peers discovered to broadcast group message")
            onResult(false)
            return
        }

        val packet = BlePacket.createGroupBroadcast(
            messageId = messageId,
            senderId = profile.userId,
            senderName = profile.displayName,
            text = text,
            hopLimit = BleConstants.MAX_RELAY_HOPS,
            visitedNodes = listOf(profile.userId)
        )

        meshRelay.markSeen(messageId)

        // Select initial fan-out peers (up to RELAY_FANOUT_COUNT)
        val fanoutPeers = peers.shuffled().take(BleConstants.RELAY_FANOUT_COUNT)
        val payload = packet.toByteArray()

        var anySuccess = false
        var completedCount = 0

        fanoutPeers.forEach { peer ->
            dispatcher.enqueue(
                OutgoingTask(
                    messageId = messageId,
                    deviceAddress = peer.bluetoothAddress,
                    payload = payload,
                    onResult = { success ->
                        completedCount++
                        if (success) anySuccess = true
                        if (completedCount >= fanoutPeers.size) {
                            onResult(anySuccess)
                        }
                    }
                )
            )
        }
    }

    private fun updateDiagnostics() {
        _diagnostics.value = BleDiagnostics(
            isBluetoothEnabled = isBluetoothEnabled(),
            isAdvertising = advertiser.isAdvertising.value,
            isScanning = scanner.isScanning.value,
            discoveredPeerCount = scanner.discoveredPeers.value.size,
            advertisingError = advertiser.advertisingError.value
        )
    }

    fun destroy() {
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        stopServices()
    }
}
