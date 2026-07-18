package com.example.ui.screens.worlds

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.MainViewModel
import com.example.data.WorldEntry
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.MineHostBrandHeader
import com.example.ui.components.MineHostButton
import com.example.ui.components.MineHostPageTitle
import com.example.ui.components.ServerThumbnail
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GreenSoft
import com.example.ui.theme.MineHostBackgroundBottom
import com.example.ui.theme.MineHostBackgroundTop
import com.example.ui.theme.MineHostBlue
import com.example.ui.theme.MineHostGreen
import com.example.ui.theme.MineHostTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorldManagerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val worlds by viewModel.worlds.collectAsState()
    val operationInProgress by viewModel.operationInProgress.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    var activateTarget by remember { mutableStateOf<WorldEntry?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(viewModel::importWorld)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWorlds()
    }

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp)
        ) {
            MineHostPageTitle(
                title = "World Manager",
                subtitle = "Import, inspect, and activate local server worlds.",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.65f)
            )
            ServerThumbnail(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(112.dp),
                corner = 24.dp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MineHostButton(
                text = "Import World ZIP",
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream")
                    )
                },
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.FileUpload,
                enabled = !operationInProgress
            )
            MineHostButton(
                text = "Refresh",
                onClick = viewModel::refreshWorlds,
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Refresh,
                outlined = true
            )
        }
        Spacer(Modifier.height(10.dp))

        if (worlds.isEmpty()) {
            GlassCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                EmptyState(
                    title = "No worlds found",
                    message = "A world will appear after the server creates one or you import a ZIP.",
                    icon = Icons.Outlined.Public,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(worlds, key = { it.path }) { world ->
                    WorldRow(
                        world = world,
                        busy = operationInProgress,
                        onActivate = {
                            if (appSettings.confirmDestructiveActions) activateTarget = world
                            else viewModel.activateWorld(world)
                        }
                    )
                }
            }
        }
    }

    activateTarget?.let { world ->
        ConfirmationDialog(
            title = "Switch active world?",
            message = "${world.name} will be used the next time the server starts. The server must be stopped.",
            confirmText = "Use World",
            onConfirm = {
                viewModel.activateWorld(world)
                activateTarget = null
            },
            onDismiss = { activateTarget = null }
        )
    }
}

@Composable
private fun WorldRow(
    world: WorldEntry,
    busy: Boolean,
    onActivate: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServerThumbnail(Modifier.size(78.dp), corner = 17.dp)
            Spacer(Modifier.size(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(world.name, style = MaterialTheme.typography.titleLarge)
                    if (world.active) {
                        Spacer(Modifier.size(8.dp))
                        StatusBadge(
                            "Active",
                            MineHostGreen,
                            GreenSoft,
                            showDot = false
                        )
                    }
                }
                Text(
                    "${formatSize(world.sizeBytes)} • ${formatDate(world.modifiedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MineHostTextSecondary
                )
                Text(
                    world.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MineHostBlue,
                    maxLines = 1
                )
            }
            if (!world.active) {
                MineHostButton(
                    text = "Use",
                    onClick = onActivate,
                    modifier = Modifier.size(width = 86.dp, height = 48.dp),
                    enabled = !busy
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(timestamp))

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824f)
    bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576f)
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
