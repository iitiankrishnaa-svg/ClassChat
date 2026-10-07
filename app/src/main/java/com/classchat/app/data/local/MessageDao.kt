package com.classchat.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.classchat.app.model.DeliveryStatus
import com.classchat.app.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)

    @Query("""
        SELECT * FROM messages 
        WHERE (senderId = :peerId AND recipientId = :myId) 
           OR (senderId = :myId AND recipientId = :peerId)
        ORDER BY timestamp ASC
    """)
    fun getDirectConversation(peerId: String, myId: String): Flow<List<Message>>

    @Query("""
        SELECT * FROM messages 
        WHERE recipientId = :groupId 
        ORDER BY timestamp ASC
    """)
    fun getGroupMessages(groupId: String = Message.CLASS_GROUP_ID): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): Message?

    @Query("SELECT COUNT(*) > 0 FROM messages WHERE id = :id")
    suspend fun hasMessage(id: String): Boolean

    @Query("""
        SELECT * FROM messages 
        WHERE id IN (
            SELECT id FROM (
                SELECT id, 
                       CASE 
                           WHEN recipientId = 'CLASS_GROUP' THEN 'CLASS_GROUP'
                           WHEN senderId = :myId THEN recipientId 
                           ELSE senderId 
                       END as convoPartner,
                       MAX(timestamp)
                FROM messages
                GROUP BY convoPartner
            )
        )
        ORDER BY timestamp DESC
    """)
    fun getRecentConversations(myId: String): Flow<List<Message>>

    @Query("SELECT COUNT(*) FROM messages")
    fun getTotalMessageCount(): Flow<Int>

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("""
        DELETE FROM messages 
        WHERE (senderId = :peerId AND recipientId = :myId) 
           OR (senderId = :myId AND recipientId = :peerId)
    """)
    suspend fun deleteDirectConversation(peerId: String, myId: String)
}
