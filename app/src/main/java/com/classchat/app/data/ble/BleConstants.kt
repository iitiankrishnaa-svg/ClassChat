package com.classchat.app.data.ble

import android.os.ParcelUuid
import java.util.UUID

object BleConstants {
    // Unique 128-bit UUID for the ClassChat classroom service
    val SERVICE_UUID: UUID = UUID.fromString("0000CC01-0000-1000-8000-00805F9B34FB")
    val PARCEL_SERVICE_UUID = ParcelUuid(SERVICE_UUID)

    // Characteristic for sending and receiving messages (Write / Write Without Response)
    val CHAR_MESSAGE_IN_UUID: UUID = UUID.fromString("0000CC02-0000-1000-8000-00805F9B34FB")

    // Characteristic for reading peer user info (Read)
    val CHAR_USER_INFO_UUID: UUID = UUID.fromString("0000CC03-0000-1000-8000-00805F9B34FB")

    // Class group broadcast recipient identifier
    const val CLASS_GROUP_ID = "CLASS_GROUP"

    // Magic header bytes for Service Data packet identification
    val PROTOCOL_MAGIC = byteArrayOf(0xCC.toByte(), 0x01.toByte())

    // Heartbeat & Pruning: Peer considered offline after 15 seconds without advertising
    const val PEER_STALE_TIMEOUT_MS = 15_000L

    // Pruning interval
    const val PEER_PRUNE_INTERVAL_MS = 5_000L

    // GATT connection timeout
    const val GATT_CONNECT_TIMEOUT_MS = 5_000L

    // Max hops for epidemic mesh relay
    const val MAX_RELAY_HOPS = 3

    // Fan-out count for group message forwarding
    const val RELAY_FANOUT_COUNT = 3

    // Desired GATT MTU size for fast message payload transfer
    const val DESIRED_MTU_SIZE = 512
}
