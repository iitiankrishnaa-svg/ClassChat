package com.classchat.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.classchat.app.model.BlePacket
import com.classchat.app.model.UserProfile
import java.nio.charset.StandardCharsets

class BleGattServer(
    private val context: Context,
    private val bluetoothManager: BluetoothManager?,
    private val onPacketReceived: (BlePacket, String) -> Unit
) {
    private val tag = "ClassChat_GattServer"

    private var gattServer: BluetoothGattServer? = null
    private var currentUserProfile: UserProfile? = null

    @SuppressLint("MissingPermission")
    fun start(profile: UserProfile): Boolean {
        currentUserProfile = profile
        if (bluetoothManager == null) {
            Log.e(tag, "BluetoothManager is null")
            return false
        }

        stop()

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                val stateStr = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                    BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                    else -> "STATE_$newState"
                }
                Log.d(tag, "Peer ${device?.address} connection state changed: $stateStr (status: $status)")
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

                if (responseNeeded && device != null) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    } catch (e: Exception) {
                        Log.e(tag, "Error sending GATT write response: ${e.message}")
                    }
                }

                if (characteristic?.uuid == BleConstants.CHAR_MESSAGE_IN_UUID && value != null) {
                    val senderAddress = device?.address ?: "unknown"
                    val packet = BlePacket.fromByteArray(value)
                    if (packet != null) {
                        Log.i(tag, "Received packet type=${packet.type} id=${packet.messageId} from ${packet.senderName} (${device?.address})")
                        onPacketReceived(packet, senderAddress)
                    } else {
                        Log.w(tag, "Received corrupted or unparseable byte packet of size ${value.size}")
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                if (characteristic?.uuid == BleConstants.CHAR_USER_INFO_UUID && device != null) {
                    val infoBytes = currentUserProfile?.displayName?.toByteArray(StandardCharsets.UTF_8) ?: byteArrayOf()
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, infoBytes)
                }
            }
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, callback)
            if (gattServer == null) {
                Log.e(tag, "Failed to open GATT server")
                return false
            }

            val service = BluetoothGattService(
                BleConstants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Message receiver characteristic (Permission: Write without pairing requirement)
            val messageInChar = BluetoothGattCharacteristic(
                BleConstants.CHAR_MESSAGE_IN_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or 
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // User info characteristic
            val userInfoChar = BluetoothGattCharacteristic(
                BleConstants.CHAR_USER_INFO_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            service.addCharacteristic(messageInChar)
            service.addCharacteristic(userInfoChar)

            val added = gattServer?.addService(service) ?: false
            Log.i(tag, "ClassChat GATT Server started, addService=$added")
            return added
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException starting GATT server: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(tag, "Exception starting GATT server: ${e.message}", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            gattServer?.clearServices()
            gattServer?.close()
            Log.i(tag, "ClassChat GATT Server closed")
        } catch (e: Exception) {
            Log.w(tag, "Exception closing GATT server: ${e.message}")
        } finally {
            gattServer = null
        }
    }

    fun updateProfile(profile: UserProfile) {
        currentUserProfile = profile
    }
}
