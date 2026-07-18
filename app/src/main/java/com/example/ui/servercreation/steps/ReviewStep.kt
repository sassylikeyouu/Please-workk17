package com.example.ui.servercreation.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.servercreation.CreateServerDraft
import com.example.ui.servercreation.NetworkMode
import com.example.ui.servercreation.WizardTheme
import com.example.ui.servercreation.components.ReviewSuccessArtwork

@Composable
fun ReviewStep(
    draft: CreateServerDraft,
    onDraftUpdate: (CreateServerDraft) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(WizardTheme.SoftBlue),
                contentAlignment = Alignment.Center
            ) {
                ReviewSuccessArtwork(
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Review Server Details",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = WizardTheme.PrimaryText
            )
            Text(
                "Please check your server configuration before creating.",
                style = MaterialTheme.typography.bodySmall,
                color = WizardTheme.SecondaryText
            )
        }

        // Summary Table
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WizardTheme.MainCardRadius))
                .border(1.dp, WizardTheme.Border, RoundedCornerShape(WizardTheme.MainCardRadius))
                .background(Color.White)
        ) {
            ReviewRow(icon = Icons.Outlined.Settings, label = "Engine", value = draft.engine?.name ?: "Bedrock")
            ReviewRow(icon = Icons.Outlined.History, label = "Version", value = draft.version)
            ReviewRow(icon = Icons.Outlined.Public, label = "World Type", value = draft.worldType.label)
            ReviewRow(icon = Icons.Outlined.Flag, label = "Difficulty", value = draft.difficulty.label)
            ReviewRow(icon = Icons.Outlined.Memory, label = "Memory", value = "${draft.memoryMb} MB")
            ReviewRow(icon = Icons.Outlined.Person, label = "Max Players", value = draft.maxPlayers.toString())
            ReviewRow(icon = Icons.Outlined.Lan, label = "Port", value = draft.port.toString())
            ReviewRow(icon = Icons.Outlined.Wifi, label = "Network Mode", value = draft.networkMode.label)
            if (draft.networkMode == NetworkMode.TUNNEL) {
                ReviewRow(icon = Icons.Outlined.Security, label = "Tunnel Provider", value = draft.tunnelProvider.label)
            }
        }
    }
}

@Composable
private fun ReviewRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = WizardTheme.PrimaryBlue
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = WizardTheme.PrimaryText
                )
            }
            Surface(
                color = WizardTheme.SoftBlue,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    value,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = WizardTheme.PrimaryBlue
                    )
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = WizardTheme.Border.copy(alpha = 0.5f))
    }
}
