package com.example.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel
import com.example.server.template.TemplateRegistry
import com.example.data.DashboardServerCardUiModel
import com.example.data.HealthSnapshot
import com.example.server.ServerStatus
import com.example.ui.components.*
import com.example.ui.navigation.MineHostDestination
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val uiState by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val status = uiState.serverStatus
    val profile = remember(selectedId, profiles) { profiles.find { it.id == selectedId } }
    val template = remember(profile) { TemplateRegistry.ALL_TEMPLATES.find { it.id == profile?.engineId } ?: TemplateRegistry.BEDROCK_CLOUDBURST_NUKKIT }
    val settings by viewModel.serverSettings.collectAsState()
    val worlds by viewModel.worlds.collectAsState()
    val activeWorld = worlds.find { it.name == settings.levelName }

    val hasProfile = profiles.isNotEmpty()
    val isOnline = status == ServerStatus.ONLINE

    // Server list calculation
    val serverCards = profiles.map { profile ->
        val isActive = profile.id == selectedId
        val profileTemplate = TemplateRegistry.ALL_TEMPLATES.find { it.id == profile.engineId } ?: TemplateRegistry.BEDROCK_CLOUDBURST_NUKKIT
        
        DashboardServerCardUiModel(
            serverId = profile.id,
            serverName = profile.name,
            engineName = profileTemplate.name,
            status = if (isActive) status else ServerStatus.STOPPED,
            currentPlayers = if (isActive) uiState.activePlayers else 0,
            maxPlayers = if (isActive) settings.maxPlayers else profile.maxPlayers,
            cpuPercent = if (isActive) uiState.cpuUsagePercent else null,
            ramUsedBytes = if (isActive) uiState.ramUsedBytes else null,
            ramLimitBytes = if (isActive) settings.memoryMb * 1024L * 1024L else profile.memoryMb * 1024L * 1024L,
            customIconPath = profile.iconPath,
            activeWorldPath = if (isActive) activeWorld?.path else "${profile.serverDirectory}/worlds/${profile.levelName}",
            isActiveRuntime = isActive,
            isAnotherServerRunning = !isActive && status != ServerStatus.STOPPED
        )
    }

    // Greeting
    val greetingTitle = "Your Servers"
    val greetingSubtitle = if (hasProfile) {
        "Manage your local Bedrock engines."
    } else {
        "Create your first local Bedrock server to get started."
    }

    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    val serverToDelete = remember(showDeleteDialog, profiles) { profiles.find { it.id == showDeleteDialog } }

    if (showDeleteDialog != null && serverToDelete != null) {
        ConfirmationDialog(
            title = "Delete Server",
            message = "Are you sure you want to delete \"${serverToDelete.name}\"? This action cannot be undone.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteServer(serverToDelete.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    MineHostScreen(
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        modifier = Modifier.background(MineHostBackgroundTop)
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            MineHostBrandHeader(
                compact = true,
                onProfile = { onNavigate(MineHostDestination.Profile.route) },
                onNotifications = { onNavigate(MineHostDestination.Notifications.route) }
            )
            
            Text(greetingTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(greetingSubtitle, style = MaterialTheme.typography.labelMedium, color = MineHostTextSecondary)
        }

        Spacer(Modifier.height(16.dp))

        // Your Servers Header (Removing redundant title if greeting is already "Your Servers", but let's make it a section)
        if (hasProfile) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Management", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MineHostBlue.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        Text(
                            profiles.size.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MineHostBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                TextButton(
                    onClick = { onNavigate(MineHostDestination.ServerCreation.route) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Server", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Servers List
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (profiles.isEmpty()) {
                CreateServerCard(onClick = { onNavigate(MineHostDestination.ServerCreation.route) })
            } else {
                serverCards.forEach { server ->
                    DashboardServerCard(
                        server = server,
                        onStart = { serverId ->
                            viewModel.selectServer(serverId)
                            viewModel.startServer()
                        },
                        onStop = { serverId ->
                            viewModel.selectServer(serverId)
                            viewModel.stopServer()
                        },
                        onDelete = { serverId ->
                            showDeleteDialog = serverId
                        },
                        onClick = { serverId ->
                            viewModel.selectServer(serverId)
                            onNavigate(MineHostDestination.ServerOverview.createRoute(serverId))
                        },
                        onFiles = { serverId ->
                            viewModel.selectServer(serverId)
                            onNavigate(MineHostDestination.ServerFiles.createRoute(serverId))
                        },
                        onSettings = { serverId ->
                            viewModel.selectServer(serverId)
                            onNavigate(MineHostDestination.ServerEdit.createRoute(serverId))
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(110.dp))
    }
}
