package com.example.ui.screens.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.MainViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.theme.BlueSoft
import com.example.ui.theme.MineHostBlue
import com.example.ui.theme.MineHostDivider
import com.example.ui.theme.MineHostGreen
import com.example.ui.theme.MineHostRed
import com.example.ui.theme.MineHostTextPrimary
import com.example.ui.theme.MineHostTextSecondary

@Composable
fun ServerPlayersScreen(viewModel: MainViewModel) {
    val players by viewModel.players.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    val labels = listOf("Online ${players.size}", "Requests", "Whitelist", "Banned")
    val filteredPlayers = players.filter { it.name.contains(query, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    val active = selectedTab == index
                    Surface(
                        onClick = { selectedTab = index },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) MineHostBlue else Color.Transparent
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else MineHostTextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(vertical = 11.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search players…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.7f),
                focusedContainerColor = Color.White
            )
        )

        GlassCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> {
                    if (filteredPlayers.isEmpty()) {
                        EmptyState(
                            title = "No online players",
                            message = "Players detected from real console join messages will appear here.",
                            icon = Icons.Outlined.GroupOff,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            item {
                                Text(
                                    "Online Players (${filteredPlayers.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            items(filteredPlayers, key = { it.name }) { player ->
                                PlayerRow(player.name, viewModel)
                                HorizontalDivider(color = MineHostDivider)
                            }
                        }
                    }
                }
                1 -> EmptyState(
                    title = "No join requests",
                    message = "Join request support depends on the selected server engine.",
                    icon = Icons.Outlined.PersonAdd,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> EmptyState(
                    title = "Whitelist management",
                    message = "Use the Console or online-player actions to add real players to the whitelist.",
                    icon = Icons.Outlined.VerifiedUser,
                    modifier = Modifier.fillMaxSize()
                )
                else -> EmptyState(
                    title = "No banned players",
                    message = "Banned player data will appear when the engine exposes it.",
                    icon = Icons.Outlined.Block,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PlayerRow(
    name: String,
    viewModel: MainViewModel
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(BlueSoft, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MineHostBlue)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(MineHostGreen, CircleShape))
                Spacer(Modifier.size(5.dp))
                Text(
                    "Online • detected from console",
                    style = MaterialTheme.typography.bodySmall,
                    color = MineHostTextSecondary
                )
            }
        }
        IconButton(
            onClick = { viewModel.sendCommand("tell $name Hello from MineHost") }
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Message",
                tint = MineHostBlue
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Actions")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Whitelist") },
                    onClick = {
                        viewModel.whitelistPlayer(name)
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Kick") },
                    onClick = {
                        viewModel.kickPlayer(name)
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ban", color = MineHostRed) },
                    onClick = {
                        viewModel.banPlayer(name)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}
