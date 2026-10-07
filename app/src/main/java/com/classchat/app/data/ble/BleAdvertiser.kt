package com.classchat.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import com.classchat.app.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class BleAdvertiser(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val tag = "ClassChat_Advertiser"

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _advertisingError = MutableStateFlow<String?>(null)
    val advertisingError: StateFlow<String?> = _advertisingError.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startAdvertising(profile: UserProfile) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _advertisingError.value = "Bluetooth is not enabled"
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            _advertisingError.value = "BLE Advertising is not supported on this device"
            Log.e(tag, "BLE Advertiser is null - device does not support peripheral mode")
            return
        }

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely
            .build()

        // Pack user info into Service Data (Magic: 2 bytes, UserId hash: 4 bytes, Name: up to 14 bytes)
        val serviceDataBytes = buildServiceData(profile)

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.PARCEL_SERVICE_UUID)
            .addServiceData(BleConstants.PARCEL_SERVICE_UUID, serviceDataBytes)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val scanResponseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                Log.i(tag, "BLE Advertising started successfully for ${profile.displayName} [${profile.userId}]")
                _isAdvertising.value = true
                _advertisingError.value = null
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                val errorMsg = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large for advertisement packet"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Advertising unsupported"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal Bluetooth error"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers active"
                    else -> "Unknown error code: $errorCode"
                }
                Log.e(tag, "BLE Advertising failed: $errorMsg (code: $errorCode)")
                _isAdvertising.value = false
                _advertisingError.value = errorMsg
            }
        }

        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponseData, callback)
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException starting advertising: ${e.message}")
            _advertisingError.value = "Bluetooth Advertise permission missing"
        } catch (e: Exception) {
            Log.e(tag, "Exception starting advertising: ${e.message}", e)
            _advertisingError.value = e.message
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            callback?.let {
                advertiser?.stopAdvertising(it)
                Log.i(tag, "BLE Advertising stopped")
            }
        } catch (e: Exception) {
            Log.w(tag, "Exception stopping advertising: ${e.message}")
        } finally {
            callback = null
            _isAdvertising.value = false
        }
    }

    private fun buildServiceData(profile: UserProfile): ByteArray {
        val magic = BleConstants.PROTOCOL_MAGIC // 2 bytes
        val idInt = try {
            profile.userId.toLong(16).toInt()
        } catch (e: Exception) {
            profile.userId.hashCode()
        }
        val nameBytes = profile.displayName.toByteArray(StandardCharsets.UTF_8).take(14).toByteArray()

        val buffer = ByteBuffer.allocate(2 + 4 + nameBytes.size)
        buffer.put(magic)
        buffer.putInt(idInt)
        buffer.put(nameBytes)
        return buffer.array()
    }
}
