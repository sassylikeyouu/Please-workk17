package com.example.ui.screens.versions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.MainViewModel
import com.example.server.ServerStatus
import com.example.server.template.ServerTemplate
import com.example.server.template.TemplateRegistry
import com.example.ui.components.GlassCard
import com.example.ui.components.MineHostBrandHeader
import com.example.ui.components.MineHostButton
import com.example.ui.components.MineHostPageTitle
import com.example.ui.components.PastelIcon
import com.example.ui.components.ServerThumbnail
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BlueSoft
import com.example.ui.theme.GreenSoft
import com.example.ui.theme.MineHostBackgroundBottom
import com.example.ui.theme.MineHostBackgroundTop
import com.example.ui.theme.MineHostBlue
import com.example.ui.theme.MineHostDivider
import com.example.ui.theme.MineHostGreen
import com.example.ui.theme.MineHostOrange
import com.example.ui.theme.MineHostRed
import com.example.ui.theme.MineHostTextSecondary
import com.example.ui.theme.MineHostYellow
import com.example.ui.theme.OrangeSoft
import com.example.ui.theme.RedSoft

@Composable
fun VersionManagerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val active by viewModel.activeTemplate.collectAsState()
    val status by viewModel.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MineHostBackgroundTop, MineHostBackgroundBottom)
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        MineHostBrandHeader(showBack = true, onBack = onBack, compact = true)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MineHostPageTitle(
                title = "Server Version Manager",
                subtitle = "Choose the Bedrock-compatible engine used by the local server."
            )

            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PastelIcon(
                        icon = Icons.Outlined.Bolt,
                        tint = MineHostOrange,
                        background = OrangeSoft,
                        size = 70.dp
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Current Server Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MineHostTextSecondary
                        )
                        Text(active.name, style = MaterialTheme.typography.headlineSmall)
                        StatusBadge(
                            text = "Installed",
                            color = MineHostGreen,
                            background = GreenSoft,
                            showDot = false
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Engine changes apply on the next server start.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MineHostTextSecondary
                        )
                    }
                    ServerThumbnail(Modifier.size(105.dp), corner = 22.dp)
                }
            }

            GlassCard(Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Row(Modifier.padding(6.dp)) {
                    VersionSummary("Installed", "1", Modifier.weight(1f), selected = true)
                    VersionSummary(
                        "Available",
                        TemplateRegistry.ALL_TEMPLATES.size.toString(),
                        Modifier.weight(1f)
                    )
                    VersionSummary("Updates", "—", Modifier.weight(1f))
                }
            }

            TemplateRegistry.ALL_TEMPLATES.forEachIndexed { index, template ->
                EngineCard(
                    template = template,
                    index = index,
                    selected = template.id == active.id,
                    canSwitch = status == ServerStatus.STOPPED ||
                        status == ServerStatus.FAILED ||
                        status == ServerStatus.CRASHED,
                    onSelect = { viewModel.setTemplate(template) }
                )
            }
            Spacer(Modifier.size(15.dp))
        }
    }
}

@Composable
private fun VersionSummary(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Column(
        modifier = modifier.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) MineHostBlue else Color.Unspecified
        )
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MineHostBlue else MineHostTextSecondary
        )
    }
}

@Composable
private fun EngineCard(
    template: ServerTemplate,
    index: Int,
    selected: Boolean,
    canSwitch: Boolean,
    onSelect: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Column {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PastelIcon(
                    icon = engineIcon(index),
                    tint = engineColor(index),
                    background = engineSoft(index),
                    size = 58.dp
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MineHostTextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge(
                            "Bedrock",
                            MineHostGreen,
                            GreenSoft,
                            showDot = false
                        )
                        StatusBadge(
                            "Local",
                            MineHostBlue,
                            BlueSoft,
                            showDot = false
                        )
                    }
                }
                if (selected) {
                    StatusBadge("Active", MineHostGreen, GreenSoft)
                } else {
                    MineHostButton(
                        text = "Use",
                        onClick = onSelect,
                        modifier = Modifier.size(width = 92.dp, height = 48.dp),
                        enabled = canSwitch
                    )
                }
            }
            HorizontalDivider(color = MineHostDivider)
            Text(
                text = if (selected) {
                    "Currently selected. Stop the server before switching."
                } else {
                    "This engine will be prepared when the server starts."
                },
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MineHostTextSecondary
            )
        }
    }
}

private fun engineIcon(index: Int): ImageVector = listOf(
    Icons.Outlined.Bolt,
    Icons.Outlined.Power,
    Icons.Outlined.Cloud,
    Icons.Outlined.Memory,
    Icons.Outlined.ViewInAr
)[index % 5]

private fun engineColor(index: Int): Color = listOf(
    MineHostOrange,
    MineHostRed,
    MineHostBlue,
    MineHostGreen,
    MineHostYellow
)[index % 5]

private fun engineSoft(index: Int): Color = listOf(
    OrangeSoft,
    RedSoft,
    BlueSoft,
    GreenSoft,
    OrangeSoft
)[index % 5]
