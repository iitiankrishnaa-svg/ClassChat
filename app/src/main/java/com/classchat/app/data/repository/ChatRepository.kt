package com.classchat.app.data.repository

import android.util.Log
import com.classchat.app.data.ble.BleConstants
import com.classchat.app.data.ble.BleManager
import com.classchat.app.data.local.MessageDao
import com.classchat.app.data.local.PreferencesManager
import com.classchat.app.model.BlePacket
import com.classchat.app.model.DeliveryStatus
import com.classchat.app.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import android.content.Context
import com.classchat.app.notification.ChatNotificationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ChatRepository(
    private val context: Context,
    private val messageDao: MessageDao,
    private val preferencesManager: PreferencesManager,
    private val bleManager: BleManager
) {
    private val tag = "ClassChat_Repo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getDirectConversation(peerId: String, myId: String): Flow<List<Message>> {
        return messageDao.getDirectConversation(peerId, myId)
    }

    fun getGroupMessages(): Flow<List<Message>> {
        return messageDao.getGroupMessages()
    }

    fun getRecentConversations(myId: String): Flow<List<Message>> {
        return messageDao.getRecentConversations(myId)
    }

    suspend fun sendDirectMessage(peerId: String, peerName: String, text: String): Message {
        val userProfile = preferencesManager.getUserProfile()
        val messageId = UUID.randomUUID().toString()

        val message = Message(
            id = messageId,
            senderId = userProfile.userId,
            senderName = userProfile.displayName,
            recipientId = peerId,
            recipientName = peerName,
            content = text.trim(),
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            deliveryStatus = DeliveryStatus.SENDING,
            hopCount = 0
        )

        // Save immediately in local database with SENDING status
        messageDao.insertMessage(message)

        // Dispatch over Bluetooth Low Energy
        bleManager.sendDirectMessage(messageId, peerId, text.trim()) { success ->
            scope.launch {
                val newStatus = if (success) DeliveryStatus.SENT_DIRECT else DeliveryStatus.FAILED
                Log.d(tag, "Message $messageId direct dispatch result: $success -> $newStatus")
                messageDao.updateMessageStatus(messageId, newStatus)
            }
        }

        return message
    }

    suspend fun sendGroupMessage(text: String): Message {
        val userProfile = preferencesManager.getUserProfile()
        val messageId = UUID.randomUUID().toString()

        val message = Message(
            id = messageId,
            senderId = userProfile.userId,
            senderName = userProfile.displayName,
            recipientId = Message.CLASS_GROUP_ID,
            content = text.trim(),
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            deliveryStatus = DeliveryStatus.SENDING,
            hopCount = 0
        )

        // Save in local database
        messageDao.insertMessage(message)

        // Broadcast over BLE
        bleManager.sendGroupMessage(messageId, text.trim()) { success ->
            scope.launch {
                val newStatus = if (success) DeliveryStatus.RELAYED else DeliveryStatus.FAILED
                Log.d(tag, "Group broadcast $messageId result: $success -> $newStatus")
                messageDao.updateMessageStatus(messageId, newStatus)
            }
        }

        return message
    }

    suspend fun retryMessage(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        if (!message.isFromMe) return

        messageDao.updateMessageStatus(messageId, DeliveryStatus.SENDING)

        if (message.isClassGroupMessage) {
            bleManager.sendGroupMessage(message.id, message.content) { success ->
                scope.launch {
                    val newStatus = if (success) DeliveryStatus.RELAYED else DeliveryStatus.FAILED
                    messageDao.updateMessageStatus(messageId, newStatus)
                }
            }
        } else {
            bleManager.sendDirectMessage(message.id, message.recipientId, message.content) { success ->
                scope.launch {
                    val newStatus = if (success) DeliveryStatus.SENT_DIRECT else DeliveryStatus.FAILED
                    messageDao.updateMessageStatus(messageId, newStatus)
                }
            }
        }
    }

    fun onPacketReceived(packet: BlePacket) {
        scope.launch {
            if (messageDao.hasMessage(packet.messageId)) {
                return@launch
            }

            val userProfile = preferencesManager.getUserProfile()
            val isGroup = (packet.type == BlePacket.TYPE_GROUP_MESSAGE)
            val hopCount = BleConstants.MAX_RELAY_HOPS - packet.hopLimit

            val message = Message(
                id = packet.messageId,
                senderId = packet.senderId,
                senderName = packet.senderName,
                recipientId = if (isGroup) Message.CLASS_GROUP_ID else userProfile.userId,
                recipientName = if (isGroup) "Class Group" else userProfile.displayName,
                content = packet.content,
                timestamp = packet.timestamp,
                isFromMe = false,
                deliveryStatus = DeliveryStatus.DELIVERED,
                hopCount = hopCount
            )

            messageDao.insertMessage(message)
            Log.i(tag, "Persisted received message ${message.id} from ${message.senderName}")

            // Trigger notification
            ChatNotificationManager.showNotification(context, packet, isGroup)
        }
    }

    fun onAckReceived(messageId: String) {
        scope.launch {
            Log.i(tag, "Marking message $messageId as DELIVERED from ACK")
            messageDao.updateMessageStatus(messageId, DeliveryStatus.DELIVERED)
        }
    }

    suspend fun clearAllMessages() {
        messageDao.clearAllMessages()
    }
}
