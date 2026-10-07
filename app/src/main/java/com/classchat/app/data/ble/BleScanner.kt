package com.classchat.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.classchat.app.model.PeerUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class BleScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val scope: CoroutineScope
) {
    private val tag = "ClassChat_Scanner"

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var pruningJob: Job? = null

    // Cache of discovered peers keyed by their unique User ID (or device address fallback)
    private val peersMap = ConcurrentHashMap<String, PeerUser>()

    // Map of device hardware address -> User ID
    private val addressToUserIdMap = ConcurrentHashMap<String, String>()

    // Persistent map of canonical User ID -> Bluetooth MAC address (survives heartbeat pruning)
    private val knownAddresses = ConcurrentHashMap<String, String>()

    private val _discoveredPeers = MutableStateFlow<List<PeerUser>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerUser>> = _discoveredPeers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startScanning(myUserId: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(tag, "Cannot scan: Bluetooth is disabled")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(tag, "BLE Scanner is null")
            return
        }

        stopScanning()

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.PARCEL_SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { handleScanResult(it, myUserId) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                results?.forEach { handleScanResult(it, myUserId) }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(tag, "BLE Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            Log.i(tag, "BLE Scanning started for ClassChat service")
            startPruningLoop()
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException starting scan: ${e.message}")
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(tag, "Exception starting scan: ${e.message}", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            scanCallback?.let {
                scanner?.stopScan(it)
                Log.i(tag, "BLE Scanning stopped")
            }
        } catch (e: Exception) {
            Log.w(tag, "Exception stopping scan: ${e.message}")
        } finally {
            scanCallback = null
            _isScanning.value = false
            pruningJob?.cancel()
            pruningJob = null
        }
    }

    private fun handleScanResult(result: ScanResult, myUserId: String) {
        val record = result.scanRecord ?: return
        val serviceData = record.getServiceData(BleConstants.PARCEL_SERVICE_UUID) ?: return

        // Verify magic bytes: 2 bytes
        if (serviceData.size < 6) return
        if (serviceData[0] != BleConstants.PROTOCOL_MAGIC[0] || serviceData[1] != BleConstants.PROTOCOL_MAGIC[1]) {
            return
        }

        val buffer = ByteBuffer.wrap(serviceData)
        buffer.getShort() // Skip magic bytes
        val peerIdInt = buffer.getInt()
        val peerId = String.format("%08X", peerIdInt)

        // Extract displayName from remaining bytes
        val nameBytes = ByteArray(buffer.remaining())
        buffer.get(nameBytes)
        val peerName = try {
            val nameStr = String(nameBytes, StandardCharsets.UTF_8).trim()
            if (nameStr.isNotEmpty()) nameStr else "Student ${result.device.address.takeLast(4)}"
        } catch (e: Exception) {
            "Student"
        }

        // Do not display ourselves in the discovered list
        if (peerId.equals(myUserId, ignoreCase = true)) {
            return
        }

        val deviceAddress = result.device.address
        knownAddresses[peerId] = deviceAddress
        addressToUserIdMap[deviceAddress] = peerId

        val updatedPeer = PeerUser(
            userId = peerId,
            displayName = peerName,
            bluetoothAddress = deviceAddress,
            rssi = result.rssi,
            lastSeenTimestamp = System.currentTimeMillis(),
            isOnline = true
        )

        peersMap[peerId] = updatedPeer
        publishPeers()
    }

    private fun startPruningLoop() {
        pruningJob?.cancel()
        pruningJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(BleConstants.PEER_PRUNE_INTERVAL_MS)
                pruneStalePeers()
            }
        }
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        var changed = false

        val iterator = peersMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val peer = entry.value
            val timeSinceLastSeen = now - peer.lastSeenTimestamp

            if (timeSinceLastSeen > (BleConstants.PEER_STALE_TIMEOUT_MS * 3)) {
                // Drop if missing for > 45 seconds
                iterator.remove()
                addressToUserIdMap.remove(peer.bluetoothAddress)
                changed = true
            } else if (timeSinceLastSeen > BleConstants.PEER_STALE_TIMEOUT_MS && peer.isOnline) {
                // Mark offline/away after 15 seconds
                peersMap[entry.key] = peer.copy(isOnline = false)
                changed = true
            }
        }

        if (changed) {
            publishPeers()
        }
    }

    private fun publishPeers() {
        _discoveredPeers.value = peersMap.values
            .sortedWith(compareByDescending<PeerUser> { it.isOnline }.thenByDescending { it.rssi })
            .toList()
    }

    fun recordPeerPresence(userId: String, displayName: String, address: String) {
        val canonicalId = userId.uppercase()
        knownAddresses[canonicalId] = address
        addressToUserIdMap[address] = canonicalId

        val existing = peersMap[canonicalId]
        val updated = PeerUser(
            userId = canonicalId,
            displayName = if (existing != null && displayName.isEmpty()) existing.displayName else displayName.ifEmpty { "Student" },
            bluetoothAddress = address,
            rssi = existing?.rssi ?: -65,
            lastSeenTimestamp = System.currentTimeMillis(),
            isOnline = true
        )
        peersMap[canonicalId] = updated
        publishPeers()
    }

    fun getDeviceAddressForPeer(userId: String): String? {
        val canonicalId = userId.uppercase()
        return knownAddresses[canonicalId]
            ?: peersMap[canonicalId]?.bluetoothAddress
            ?: knownAddresses.entries.find { it.key.equals(userId, ignoreCase = true) }?.value
            ?: addressToUserIdMap.entries.find { it.value.equals(userId, ignoreCase = true) }?.key
    }

    fun getPeer(userId: String): PeerUser? {
        val canonicalId = userId.uppercase()
        return peersMap[canonicalId] ?: peersMap.values.find { it.userId.equals(userId, ignoreCase = true) }
    }
}
