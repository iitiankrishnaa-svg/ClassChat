package com.classchat.app.data.ble

import android.util.Log
import com.classchat.app.model.BlePacket
import com.classchat.app.model.PeerUser
import java.util.LinkedHashMap

class MeshRelayEngine(
    private val dispatcher: BleQueueDispatcher
) {
    private val tag = "ClassChat_MeshRelay"

    // Deduplication LRU cache capped at 500 recent messages
    private val lock = Any()
    private val seenMessageIds = object : LinkedHashMap<String, Boolean>(500, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 500
        }
    }

    fun isDuplicate(messageId: String): Boolean {
        synchronized(lock) {
            if (seenMessageIds.containsKey(messageId)) {
                return true
            }
            seenMessageIds[messageId] = true
            return false
        }
    }

    fun markSeen(messageId: String) {
        synchronized(lock) {
            seenMessageIds[messageId] = true
        }
    }

    /**
     * Forwards a classroom broadcast packet to a subset of unvisited nearby peers.
     */
    fun relayGroupBroadcast(
        packet: BlePacket,
        currentUserId: String,
        availablePeers: List<PeerUser>
    ) {
        if (packet.hopLimit <= 1) {
            Log.d(tag, "Packet ${packet.messageId} reached TTL hop limit, stopping relay")
            return
        }

        val updatedVisited = (packet.visitedNodes + currentUserId).distinct()
        val relayedPacket = packet.copy(
            hopLimit = packet.hopLimit - 1,
            visitedNodes = updatedVisited
        )
        val payload = relayedPacket.toByteArray()

        // Filter active peers who have not yet seen this packet
        val candidates = availablePeers
            .filter { it.isOnline && !updatedVisited.contains(it.userId) }
            .shuffled()
            .take(BleConstants.RELAY_FANOUT_COUNT)

        Log.i(tag, "Relaying broadcast ${packet.messageId} (hopLimit ${relayedPacket.hopLimit}) to ${candidates.size} peers")

        candidates.forEach { peer ->
            dispatcher.enqueue(
                OutgoingTask(
                    messageId = packet.messageId,
                    deviceAddress = peer.bluetoothAddress,
                    payload = payload,
                    onResult = { success ->
                        Log.d(tag, "Relay to ${peer.displayName} (${peer.bluetoothAddress}) result: $success")
                    }
                )
            )
        }
    }
}
