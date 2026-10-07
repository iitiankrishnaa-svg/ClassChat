package com.classchat.app.model

/**
 * Represents a nearby peer student discovered over Bluetooth Low Energy.
 */
data class PeerUser(
    val userId: String,
    val displayName: String,
    val bluetoothAddress: String,
    val rssi: Int = -70,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true
) {
    val signalStrength: SignalStrength
        get() = when {
            rssi >= -65 -> SignalStrength.EXCELLENT
            rssi >= -78 -> SignalStrength.GOOD
            rssi >= -88 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }
}

enum class SignalStrength {
    EXCELLENT,
    GOOD,
    FAIR,
    WEAK
}
