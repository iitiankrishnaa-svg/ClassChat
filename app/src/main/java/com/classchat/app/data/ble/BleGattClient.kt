package com.classchat.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class BleGattClient(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val tag = "ClassChat_GattClient"

    @SuppressLint("MissingPermission")
    suspend fun sendBytesToDevice(
        deviceAddress: String,
        payload: ByteArray,
        timeoutMs: Long = BleConstants.GATT_CONNECT_TIMEOUT_MS
    ): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(tag, "Cannot send: Bluetooth is disabled")
            return false
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            Log.e(tag, "Invalid Bluetooth address: $deviceAddress", e)
            return false
        }

        val resultDeferred = CompletableDeferred<Boolean>()
        var activeGatt: BluetoothGatt? = null

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                Log.d(tag, "Client connection to $deviceAddress: status=$status, newState=$newState")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(tag, "GATT connection failure with status=$status for $deviceAddress")
                    resultDeferred.complete(false)
                    closeGatt(gatt)
                    return
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(tag, "Connected to $deviceAddress. Requesting MTU ${BleConstants.DESIRED_MTU_SIZE}")
                    if (gatt != null) {
                        try {
                            if (!gatt.requestMtu(BleConstants.DESIRED_MTU_SIZE)) {
                                Log.w(tag, "requestMtu returned false, proceeding to service discovery")
                                gatt.discoverServices()
                            }
                        } catch (e: Exception) {
                            gatt.discoverServices()
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(tag, "Disconnected from $deviceAddress")
                    closeGatt(gatt)
                    if (!resultDeferred.isCompleted) {
                        resultDeferred.complete(false)
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                Log.d(tag, "MTU changed to $mtu for $deviceAddress (status: $status). Discovering services...")
                try {
                    gatt?.discoverServices()
                } catch (e: Exception) {
                    Log.e(tag, "Error discovering services: ${e.message}")
                    resultDeferred.complete(false)
                    closeGatt(gatt)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                    Log.w(tag, "Service discovery failed with status $status for $deviceAddress")
                    resultDeferred.complete(false)
                    closeGatt(gatt)
                    return
                }

                val service = gatt.getService(BleConstants.SERVICE_UUID)
                if (service == null) {
                    Log.w(tag, "ClassChat Service not found on $deviceAddress")
                    resultDeferred.complete(false)
                    closeGatt(gatt)
                    return
                }

                val characteristic = service.getCharacteristic(BleConstants.CHAR_MESSAGE_IN_UUID)
                if (characteristic == null) {
                    Log.w(tag, "Message characteristic not found on $deviceAddress")
                    resultDeferred.complete(false)
                    closeGatt(gatt)
                    return
                }

                writePayload(gatt, characteristic, payload)
            }

            private fun writePayload(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                data: ByteArray
            ) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val writeStatus = gatt.writeCharacteristic(
                            characteristic,
                            data,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                        Log.d(tag, "writeCharacteristic (API 33+) result: $writeStatus to $deviceAddress")
                        if (writeStatus != BluetoothGatt.GATT_SUCCESS) {
                            resultDeferred.complete(false)
                            closeGatt(gatt)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = data
                        @Suppress("DEPRECATION")
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        @Suppress("DEPRECATION")
                        val success = gatt.writeCharacteristic(characteristic)
                        Log.d(tag, "writeCharacteristic legacy result: $success to $deviceAddress")
                        if (!success) {
                            resultDeferred.complete(false)
                            closeGatt(gatt)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Exception during characteristic write: ${e.message}", e)
                    resultDeferred.complete(false)
                    closeGatt(gatt)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                Log.i(tag, "Packet write completed to $deviceAddress with status: $status")
                val isSuccess = (status == BluetoothGatt.GATT_SUCCESS)
                resultDeferred.complete(isSuccess)
                closeGatt(gatt)
            }
        }

        try {
            Log.d(tag, "Initiating transient GATT connection to $deviceAddress")
            activeGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to connectGatt to $deviceAddress: ${e.message}", e)
            return false
        }

        val success = withTimeoutOrNull(timeoutMs) {
            resultDeferred.await()
        } ?: false

        if (!success) {
            closeGatt(activeGatt)
        }

        return success
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(gatt: BluetoothGatt?) {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.w(tag, "Error closing GATT: ${e.message}")
        }
    }
}
