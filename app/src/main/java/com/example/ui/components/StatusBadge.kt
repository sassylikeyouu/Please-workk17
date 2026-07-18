package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.ServerStatus
import com.example.ui.theme.*

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = true
) {
    Row(
        modifier = modifier.background(background, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (showDot) Box(Modifier.size(7.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
fun StatusBadge(status: ServerStatus, modifier: Modifier = Modifier) {
    val (text, color, bg) = when (status) {
        ServerStatus.ONLINE -> Triple("Online", MineHostGreen, GreenSoft)
        ServerStatus.STARTING -> Triple("Starting", MineHostBlue, BlueSoft)
        ServerStatus.PREPARING -> Triple("Preparing", MineHostBlue, BlueSoft)
        ServerStatus.DOWNLOADING -> Triple("Downloading", MineHostBlue, BlueSoft)
        ServerStatus.STOPPING -> Triple("Stopping", MineHostOrange, OrangeSoft)
        ServerStatus.CRASHED -> Triple("Crashed", MineHostRed, RedSoft)
        ServerStatus.FAILED -> Triple("Failed", MineHostRed, RedSoft)
        ServerStatus.STOPPED -> Triple("Offline", MineHostTextSecondary, MineHostSurfaceVariant)
    }
    StatusBadge(text, color, bg, modifier)
}
