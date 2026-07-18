package com.example.data

import com.example.server.ServerStatus

data class RuntimeMetrics(
    val cpuPercent: Float? = null,
    val ramBytes: Long? = null,
    val serverDataBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val tps: Float? = null,
    val playersOnline: Int? = null,
    val uptimeMillis: Long? = null,
    val processId: Long? = null,
    val isPlayerTrackingAvailable: Boolean = false
)

data class DashboardUiState(
    val hasServerProfile: Boolean,
    val serverStatus: ServerStatus,
    val isProcessAlive: Boolean,
    val isServerReady: Boolean,
    val totalServerCount: Int,
    val onlineServerCount: Int,
    val activePlayers: Int?,
    val ramUsedBytes: Long?,
    val cpuUsagePercent: Float?,
    val tps: Float?,
    val pingMs: Int?,
    val healthSummary: HealthSnapshot?,
    val uptimeMillis: Long?,
    val latestMetricTimestamp: Long?
) {
    companion object {
        fun preview() = DashboardUiState(
            hasServerProfile = true,
            serverStatus = ServerStatus.ONLINE,
            isProcessAlive = true,
            isServerReady = true,
            totalServerCount = 1,
            onlineServerCount = 1,
            activePlayers = 2,
            ramUsedBytes = 412 * 1024 * 1024L,
            cpuUsagePercent = 12.5f,
            tps = 20.0f,
            pingMs = 45,
            healthSummary = HealthSnapshot(status = ServerStatus.ONLINE, score = 100),
            uptimeMillis = 1234567L,
            latestMetricTimestamp = System.currentTimeMillis()
        )
    }
}

data class PlayerSession(
    val name: String,
    val joinedAt: Long,
    val lastSeenAt: Long,
    val online: Boolean = true
)

data class PluginEntry(
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val enabled: Boolean,
    val modifiedAt: Long
)

data class BackupEntry(
    val fileName: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val absolutePath: String
)

data class WorldEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val active: Boolean
)

data class CrashEntry(
    val id: String,
    val timestamp: Long,
    val title: String,
    val details: String,
    val severity: CrashSeverity
)

enum class CrashSeverity { WARNING, ERROR, CRITICAL }

data class ActivityEvent(
    val id: String,
    val timestamp: Long,
    val title: String,
    val description: String,
    val type: ActivityType
)

enum class ActivityType { INFO, SUCCESS, WARNING, ERROR }

data class ServerSettingsState(
    val serverName: String = "Local Bedrock Server",
    val gameMode: String = "survival",
    val difficulty: String = "normal",
    val maxPlayers: Int = 10,
    val whitelistEnabled: Boolean = false,
    val onlineMode: Boolean = true,
    val viewDistance: Int = 8,
    val port: Int = 19132,
    val levelName: String = "world",
    val memoryMb: Int = 600,
    val autoRestart: Boolean = true,
    val autoBackup: Boolean = false
)

data class AppSettingsState(
    val darkMode: Boolean = false,
    val language: String = "English",
    val startupScreen: String = "Dashboard",
    val dataSaver: Boolean = false,
    val hapticFeedback: Boolean = true,
    val confirmDestructiveActions: Boolean = true
)

data class NotificationSettingsState(
    val serverOfflineAlerts: Boolean = true,
    val crashAlerts: Boolean = true,
    val backupReminders: Boolean = true,
    val pluginUpdateAlerts: Boolean = false,
    val marketplaceAnnouncements: Boolean = false,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val quietHoursEnabled: Boolean = false
)

data class HealthSnapshot(
    val status: ServerStatus = ServerStatus.STOPPED,
    val score: Int = 0,
    val warnings: List<String> = emptyList(),
    val checkedAt: Long = System.currentTimeMillis()
)

data class AssistantMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class OperationResult(
    val success: Boolean,
    val message: String
)

data class LocalServerProfile(
    val created: Boolean,
    val serverName: String,
    val templateId: String,
    val levelName: String,
    val iconPath: String?,
    val createdAt: Long
)

data class ServerCreationDraft(
    val name: String,
    val engineId: String,
    val levelName: String = "world",
    val iconPath: String? = null,
    val port: Int = 19132,
    val memoryMb: Int = 600,
    val maxPlayers: Int = 10
)

data class DashboardServerCardUiModel(
    val serverId: String,
    val serverName: String,
    val engineName: String,
    val status: ServerStatus,
    val currentPlayers: Int?,
    val maxPlayers: Int,
    val cpuPercent: Float?,
    val ramUsedBytes: Long?,
    val ramLimitBytes: Long?,
    val customIconPath: String?,
    val activeWorldPath: String?,
    val isActiveRuntime: Boolean,
    val isAnotherServerRunning: Boolean
)

sealed interface ServerEditorMode {
    data object Create : ServerEditorMode
    data class Edit(val serverId: String) : ServerEditorMode
}
