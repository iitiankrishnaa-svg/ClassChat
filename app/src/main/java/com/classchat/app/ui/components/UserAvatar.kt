package com.classchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val AvatarColors = listOf(
    Pair(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
    Pair(Color(0xFF10B981), Color(0xFF047857)),
    Pair(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
    Pair(Color(0xFFF59E0B), Color(0xFFD97706)),
    Pair(Color(0xFFEC4899), Color(0xFFBE185D)),
    Pair(Color(0xFF06B6D4), Color(0xFF0E7490)),
    Pair(Color(0xFFF97316), Color(0xFFC2410C))
)

@Composable
fun UserAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val initials = name.trim().split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercase() }
        .joinToString("")
        .ifEmpty { "?" }

    val colorIndex = abs(name.hashCode()) % AvatarColors.size
    val (colorStart, colorEnd) = AvatarColors[colorIndex]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}
