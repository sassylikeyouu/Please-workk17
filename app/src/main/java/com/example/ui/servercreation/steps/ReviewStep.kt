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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.SafeResourceImage
import com.example.R
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
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
            ReviewRow(iconResId = R.drawable.review_engine_icon, label = "Engine", value = draft.engine?.name ?: "Bedrock")
            ReviewRow(iconResId = R.drawable.review_version_icon, label = "Version", value = draft.version)
            ReviewRow(iconResId = R.drawable.review_world_icon, label = "World Type", value = draft.worldType.label)
            ReviewRow(iconResId = R.drawable.difficulty_normal, label = "Difficulty", value = draft.difficulty.label)
            ReviewRow(iconResId = R.drawable.memory_icon, label = "Memory", value = "${draft.memoryMb} MB")
            ReviewRow(iconResId = R.drawable.max_players_icon, label = "Max Players", value = draft.maxPlayers.toString())
            ReviewRow(iconResId = R.drawable.port_icon, label = "Port", value = draft.port.toString())
            ReviewRow(iconResId = R.drawable.network_local_icon, label = "Network Mode", value = draft.networkMode.label)
            if (draft.networkMode == NetworkMode.TUNNEL) {
                ReviewRow(iconResId = R.drawable.network_tunnel, label = "Tunnel Provider", value = draft.tunnelProvider.label)
            }
        }
    }
}

@Composable
private fun ReviewRow(
    @androidx.annotation.DrawableRes iconResId: Int,
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
                SafeResourceImage(
                    resId = iconResId,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
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
