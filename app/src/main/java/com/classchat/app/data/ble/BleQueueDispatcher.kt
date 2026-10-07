package com.classchat.app.data.ble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OutgoingTask(
    val messageId: String,
    val deviceAddress: String,
    val payload: ByteArray,
    val onResult: (Boolean) -> Unit
)

class BleQueueDispatcher(
    private val client: BleGattClient,
    private val scope: CoroutineScope
) {
    private val tag = "ClassChat_Dispatcher"
    private val queueChannel = Channel<OutgoingTask>(Channel.UNLIMITED)

    init {
        startWorker()
    }

    private fun startWorker() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val task = queueChannel.receive()
                    processTask(task)
                } catch (e: Exception) {
                    Log.e(tag, "Error in queue worker loop: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun processTask(task: OutgoingTask) {
        Log.d(tag, "Processing outgoing transmission for message ${task.messageId} to ${task.deviceAddress}")

        var success = client.sendBytesToDevice(task.deviceAddress, task.payload)

        // Retry once on failure after small stabilization delay
        if (!success) {
            Log.w(tag, "Initial send failed to ${task.deviceAddress}. Retrying after 300ms...")
            delay(300)
            success = client.sendBytesToDevice(task.deviceAddress, task.payload)
        }

        try {
            task.onResult(success)
        } catch (e: Exception) {
            Log.e(tag, "Error notifying task completion: ${e.message}")
        }

        // Add 100ms breathing room for Android Bluetooth hardware before next peer connection
        delay(100)
    }

    fun enqueue(task: OutgoingTask) {
        queueChannel.trySend(task)
    }
}
