package com.classchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classchat.app.model.SignalStrength
import com.classchat.app.ui.theme.SignalExcellent
import com.classchat.app.ui.theme.SignalFair
import com.classchat.app.ui.theme.SignalGood
import com.classchat.app.ui.theme.SignalWeak

@Composable
fun SignalIndicator(
    signalStrength: SignalStrength,
    isOnline: Boolean = true,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeColor = when (signalStrength) {
        SignalStrength.EXCELLENT -> SignalExcellent
        SignalStrength.GOOD -> SignalGood
        SignalStrength.FAIR -> SignalFair
        SignalStrength.WEAK -> SignalWeak
    }

    val activeBars = when (signalStrength) {
        SignalStrength.EXCELLENT -> 4
        SignalStrength.GOOD -> 3
        SignalStrength.FAIR -> 2
        SignalStrength.WEAK -> 1
    }

    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (!isOnline) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
            if (showLabel) {
                Text(
                    text = "Away",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        } else {
            val barHeights = listOf(4.dp, 7.dp, 10.dp, 13.dp)
            barHeights.forEachIndexed { index, height ->
                val isBarActive = index < activeBars
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isBarActive) activeColor else inactiveColor)
                )
            }

            if (showLabel) {
                Text(
                    text = when (signalStrength) {
                        SignalStrength.EXCELLENT -> "Nearby"
                        SignalStrength.GOOD -> "Good"
                        SignalStrength.FAIR -> "Fair"
                        SignalStrength.WEAK -> "Far"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = activeColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}
