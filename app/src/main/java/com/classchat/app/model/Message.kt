package com.classchat.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["recipientId"]),
        Index(value = ["senderId"]),
        Index(value = ["timestamp"])
    ]
)
data class Message(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val recipientName: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val hopCount: Int = 0
) {
    val isClassGroupMessage: Boolean
        get() = recipientId == CLASS_GROUP_ID

    companion object {
        const val CLASS_GROUP_ID = "CLASS_GROUP"
    }
}
