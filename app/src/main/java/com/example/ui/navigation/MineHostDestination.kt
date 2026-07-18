package com.example.ui.navigation

sealed class MineHostDestination(val route: String) {
    data object Dashboard : MineHostDestination("dashboard")
    data object Plugins : MineHostDestination("plugins")
    data object Files : MineHostDestination("files")
    data object ServerFiles : MineHostDestination("files/{serverId}") { fun createRoute(serverId: String) = "files/$serverId" }
    data object Profile : MineHostDestination("profile")

    data object LegacyServer : MineHostDestination("legacy_server")
    data object ServerOverview : MineHostDestination("server_overview/{serverId}") { fun createRoute(serverId: String) = "server_overview/$serverId" }
    data object ServerCreation : MineHostDestination("server/create")
    data object ServerEdit : MineHostDestination("server/{serverId}/edit") { fun createRoute(serverId: String) = "server/$serverId/edit" }
    data object Marketplace : MineHostDestination("marketplace")
    data object PerformanceMonitor : MineHostDestination("performance_monitor")
    data object VersionManager : MineHostDestination("version_manager")
    data object BackupManager : MineHostDestination("backup_manager")
    data object WorldManager : MineHostDestination("world_manager")

    data object AppSettings : MineHostDestination("app_settings")
    data object Account : MineHostDestination("account")
    data object Notifications : MineHostDestination("notifications")

    data object Activity : MineHostDestination("activity/{serverId}") { fun createRoute(serverId: String) = "activity/$serverId" }
    data object AiAssistant : MineHostDestination("ai_assistant/{serverId}") { fun createRoute(serverId: String) = "ai_assistant/$serverId" }
    data object AiAssistantTab : MineHostDestination("ai_assistant_global")
    data object PerformanceRecommendations : MineHostDestination("performance/{serverId}") { fun createRoute(serverId: String) = "performance/$serverId" }
    data object CrashAnalysis : MineHostDestination("crash_analysis/{serverId}") { fun createRoute(serverId: String) = "crash_analysis/$serverId" }
    data object ServerHealth : MineHostDestination("server_health/{serverId}") { fun createRoute(serverId: String) = "server_health/$serverId" }
    data object AutoOptimization : MineHostDestination("auto_optimization/{serverId}") { fun createRoute(serverId: String) = "auto_optimization/$serverId" }
}
