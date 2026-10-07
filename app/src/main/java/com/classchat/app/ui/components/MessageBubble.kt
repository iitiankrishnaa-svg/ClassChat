package com.classchat.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classchat.app.model.DeliveryStatus
import com.classchat.app.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    isGroupChat: Boolean = false,
    onRetryClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isFromMe = message.isFromMe
    val alignment = if (isFromMe) Alignment.End else Alignment.Start

    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    val bubbleColor = if (isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isFromMe) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val subTextColor = if (isFromMe) {
        Color.White.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }

    val timeFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = alignment
    ) {
        // Show sender name and Local ID in group chat if received
        if (isGroupChat && !isFromMe) {
            Row(
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (message.senderId.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Local ID: ${message.senderId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontSize = 15.sp
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mesh relay hops tag
                    if (message.hopCount > 0) {
                        Text(
                            text = if (message.hopCount == 1) "1 hop" else "${message.hopCount} hops",
                            style = MaterialTheme.typography.labelSmall,
                            color = subTextColor,
                            fontSize = 9.sp
                        )
                    }

                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor,
                        fontSize = 10.sp
                    )

                    // Delivery status icon for sent messages
                    if (isFromMe) {
                        DeliveryStatusIndicator(
                            status = message.deliveryStatus,
                            tint = subTextColor,
                            onRetry = { onRetryClick?.invoke(message.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatusIndicator(
    status: DeliveryStatus,
    tint: Color,
    onRetry: () -> Unit
) {
    when (status) {
        DeliveryStatus.PENDING,
        DeliveryStatus.SENDING -> {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Sending",
                tint = tint,
                modifier = Modifier.size(11.dp)
            )
        }
        DeliveryStatus.SENT_DIRECT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent Direct",
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
        }
        DeliveryStatus.RELAYED,
        DeliveryStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = Color(0xFF67E8F9), // Subtle cyan tick for delivered
                modifier = Modifier.size(13.dp)
            )
        }
        DeliveryStatus.FAILED -> {
            Row(
                modifier = Modifier.clickable { onRetry() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Failed - Tap to retry",
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "Retry",
                    color = Color(0xFFFCA5A5),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
