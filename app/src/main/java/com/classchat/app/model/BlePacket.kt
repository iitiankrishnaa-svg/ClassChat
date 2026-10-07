package com.classchat.app.model

import com.google.gson.Gson
import java.nio.charset.StandardCharsets

data class BlePacket(
    val type: Int,
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hopLimit: Int = 3,
    val visitedNodes: List<String> = emptyList()
) {
    fun toByteArray(): ByteArray {
        val json = gson.toJson(this)
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        const val TYPE_DIRECT_MESSAGE = 1
        const val TYPE_GROUP_MESSAGE = 2
        const val TYPE_ACK = 3

        private val gson = Gson()

        fun fromByteArray(bytes: ByteArray): BlePacket? {
            return try {
                val json = String(bytes, StandardCharsets.UTF_8)
                gson.fromJson(json, BlePacket::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun createDirectMessage(
            messageId: String,
            senderId: String,
            senderName: String,
            recipientId: String,
            text: String
        ): BlePacket {
            return BlePacket(
                type = TYPE_DIRECT_MESSAGE,
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                recipientId = recipientId,
                content = text,
                hopLimit = 1,
                visitedNodes = listOf(senderId)
            )
        }

        fun createGroupBroadcast(
            messageId: String,
            senderId: String,
            senderName: String,
            text: String,
            hopLimit: Int = 3,
            visitedNodes: List<String> = listOf(senderId)
        ): BlePacket {
            return BlePacket(
                type = TYPE_GROUP_MESSAGE,
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                recipientId = Message.CLASS_GROUP_ID,
                content = text,
                hopLimit = hopLimit,
                visitedNodes = visitedNodes
            )
        }

        fun createAck(
            messageId: String,
            senderId: String,
            recipientId: String
        ): BlePacket {
            return BlePacket(
                type = TYPE_ACK,
                messageId = messageId,
                senderId = senderId,
                senderName = "",
                recipientId = recipientId,
                content = "ACK",
                hopLimit = 1,
                visitedNodes = emptyList()
            )
        }
    }
}
