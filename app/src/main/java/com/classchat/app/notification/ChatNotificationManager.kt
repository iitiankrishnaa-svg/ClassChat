package com.classchat.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.classchat.app.MainActivity
import com.classchat.app.R
import com.classchat.app.model.BlePacket
import java.util.LinkedHashMap

object ChatNotificationManager {
    private const val TAG = "ChatNotificationManager"

    const val CHANNEL_ID = "classchat_messages_channel"
    private const val CHANNEL_NAME = "Chat Messages"

    const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
    const val EXTRA_PEER_ID = "extra_peer_id"
    const val EXTRA_PEER_NAME = "extra_peer_name"
    const val EXTRA_IS_GROUP = "extra_is_group"

    // Tracks the currently opened conversation peer ID in ChatScreen
    @Volatile
    var activeConversationPeerId: String? = null

    // Tracks whether MainActivity is in foreground and resumed
    @Volatile
    var isAppInForeground: Boolean = false

    // Deduplication cache for notified message IDs
    private val notifiedLock = Any()
    private val notifiedMessageIds = object : LinkedHashMap<String, Boolean>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 200
        }
    }

    fun init(context: Context) {
        createNotificationChannel(context)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming personal and classroom messages"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '$CHANNEL_NAME' initialized")
        }
    }

    fun showNotification(context: Context, packet: BlePacket, isGroup: Boolean) {
        // 1. Deduplication check
        synchronized(notifiedLock) {
            if (notifiedMessageIds.containsKey(packet.messageId)) {
                Log.d(TAG, "Message ${packet.messageId} already notified, skipping")
                return
            }
            notifiedMessageIds[packet.messageId] = true
        }

        // 2. Suppress notification if user is actively viewing this exact conversation right now
        val targetConversationId = if (isGroup) "CLASS_GROUP" else packet.senderId
        if (isAppInForeground && activeConversationPeerId.equals(targetConversationId, ignoreCase = true)) {
            Log.d(TAG, "User is actively viewing conversation with $targetConversationId, suppressing notification")
            return
        }

        // 3. Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted, cannot display notification")
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Ensure channel exists
        createNotificationChannel(context)

        // 4. Build intent to open the conversation directly
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, if (isGroup) "group_chat" else "direct_chat")
            putExtra(EXTRA_PEER_ID, packet.senderId)
            putExtra(EXTRA_PEER_NAME, packet.senderName)
            putExtra(EXTRA_IS_GROUP, isGroup)
        }

        val notificationId = if (isGroup) 2001 else packet.senderId.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val baseName = packet.senderName.ifEmpty { "Classmate" }
        val senderTitle = if (isGroup) {
            if (packet.senderId.isNotEmpty()) {
                "$baseName (Local ID: ${packet.senderId}) • Class Group"
            } else {
                "$baseName (Class Group)"
            }
        } else {
            if (packet.senderId.isNotEmpty()) {
                "$baseName (Local ID: ${packet.senderId})"
            } else {
                baseName
            }
        }

        val messageText = packet.content

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderTitle)
            .setContentText(messageText)
            .setSubText("ClassChat")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(senderTitle)
                    .setSummaryText("ClassChat")
                    .bigText(messageText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Log.i(TAG, "Notification posted for message ${packet.messageId} from $senderTitle")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException posting notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception posting notification: ${e.message}", e)
        }
    }
}
