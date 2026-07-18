package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.notifications.MineHostNotificationManager
import com.example.server.FileInfo
import com.example.server.NetworkUtils
import com.example.server.ServerManager
import com.example.server.ServerStatus
import com.example.server.template.ServerTemplate
import com.example.server.template.TemplateRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val prefs = application.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
    private val uiPrefs = application.getSharedPreferences("minehost_ui_prefs", Context.MODE_PRIVATE)
    private val metricsReader = ProcessMetricsReader(application.applicationContext)
    private val notificationManager = MineHostNotificationManager(appContext)
    private val profileRepository = ServerProfileRepository(appContext)
    
    private val _selectedServerId = MutableStateFlow(prefs.getString("selected_server_id", null))
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    val profiles: StateFlow<List<ServerProfile>> = profileRepository.profiles

    private var autoRestartJob: Job? = null
    private var autoRestartAttempts = 0
    private var manualStopRequested = false

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _ipAddress = MutableStateFlow("Unknown")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _memoryMb = MutableStateFlow(prefs.getInt("memory_mb", 600))
    val memoryMb: StateFlow<Int> = _memoryMb.asStateFlow()

    private val _onlineMode = MutableStateFlow(prefs.getBoolean("online_mode", true))
    val onlineMode: StateFlow<Boolean> = _onlineMode.asStateFlow()

    private val initialTemplate = TemplateRegistry.ALL_TEMPLATES.firstOrNull {
        it.id == prefs.getString("template_id", TemplateRegistry.BEDROCK_CLOUDBURST_NUKKIT.id)
    } ?: TemplateRegistry.BEDROCK_CLOUDBURST_NUKKIT

    private val _activeTemplate = MutableStateFlow(initialTemplate)
    val activeTemplate: StateFlow<ServerTemplate> = _activeTemplate.asStateFlow()

    private val _currentFiles = MutableStateFlow<List<FileInfo>>(emptyList())
    val currentFiles: StateFlow<List<FileInfo>> = _currentFiles.asStateFlow()

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _selectedFilePath = MutableStateFlow<String?>(null)
    val selectedFilePath: StateFlow<String?> = _selectedFilePath.asStateFlow()

    private val _selectedFileContent = MutableStateFlow<String?>(null)
    val selectedFileContent: StateFlow<String?> = _selectedFileContent.asStateFlow()

    private val _plugins = MutableStateFlow<List<PluginEntry>>(emptyList())
    val plugins: StateFlow<List<PluginEntry>> = _plugins.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupEntry>>(emptyList())
    val backups: StateFlow<List<BackupEntry>> = _backups.asStateFlow()

    private val _worlds = MutableStateFlow<List<WorldEntry>>(emptyList())
    val worlds: StateFlow<List<WorldEntry>> = _worlds.asStateFlow()

    private val _serverSettings = MutableStateFlow(ServerSettingsState(memoryMb = _memoryMb.value, onlineMode = _onlineMode.value))
    val serverSettings: StateFlow<ServerSettingsState> = _serverSettings.asStateFlow()

    private val _players = MutableStateFlow<List<PlayerSession>>(emptyList())
    val players: StateFlow<List<PlayerSession>> = _players.asStateFlow()

    private val _metrics = MutableStateFlow(RuntimeMetrics())
    val metrics: StateFlow<RuntimeMetrics> = _metrics.asStateFlow()

    private val _dashboardUiState = MutableStateFlow(
        DashboardUiState(
            hasServerProfile = false,
            serverStatus = ServerStatus.STOPPED,
            isProcessAlive = false,
            isServerReady = false,
            totalServerCount = 0,
            onlineServerCount = 0,
            activePlayers = null,
            ramUsedBytes = null,
            cpuUsagePercent = null,
            tps = null,
            pingMs = null,
            healthSummary = null,
            uptimeMillis = null,
            latestMetricTimestamp = null
        )
    )
    val dashboardUiState: StateFlow<DashboardUiState> = _dashboardUiState.asStateFlow()

    private val _activities = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val activities: StateFlow<List<ActivityEvent>> = _activities.asStateFlow()

    private val _crashes = MutableStateFlow<List<CrashEntry>>(emptyList())
    val crashes: StateFlow<List<CrashEntry>> = _crashes.asStateFlow()

    private val _health = MutableStateFlow(HealthSnapshot())
    val health: StateFlow<HealthSnapshot> = _health.asStateFlow()

    private val _recommendations = MutableStateFlow<List<String>>(emptyList())
    val recommendations: StateFlow<List<String>> = _recommendations.asStateFlow()

    private val _assistantMessages = MutableStateFlow(
        listOf(
            AssistantMessage(
                id = UUID.randomUUID().toString(),
                fromUser = false,
                text = "I can inspect your local server status, settings, and recent logs. Ask me about crashes, performance, or configuration."
            )
        )
    )
    val assistantMessages: StateFlow<List<AssistantMessage>> = _assistantMessages.asStateFlow()

    private val _appSettings = MutableStateFlow(
        AppSettingsState(
            darkMode = uiPrefs.getBoolean("dark_mode", false),
            language = uiPrefs.getString("language", "English") ?: "English",
            startupScreen = uiPrefs.getString("startup_screen", "Dashboard") ?: "Dashboard",
            dataSaver = uiPrefs.getBoolean("data_saver", false),
            hapticFeedback = uiPrefs.getBoolean("haptic", true),
            confirmDestructiveActions = uiPrefs.getBoolean("confirm_destructive", true)
        )
    )
    val appSettings: StateFlow<AppSettingsState> = _appSettings.asStateFlow()

    private val _notificationSettings = MutableStateFlow(
        NotificationSettingsState(
            serverOfflineAlerts = uiPrefs.getBoolean("notify_offline", true),
            crashAlerts = uiPrefs.getBoolean("notify_crash", true),
            backupReminders = uiPrefs.getBoolean("notify_backup", true),
            pluginUpdateAlerts = uiPrefs.getBoolean("notify_plugins", false),
            marketplaceAnnouncements = uiPrefs.getBoolean("notify_marketplace", false),
            sound = uiPrefs.getBoolean("notify_sound", true),
            vibration = uiPrefs.getBoolean("notify_vibration", true),
            quietHoursEnabled = uiPrefs.getBoolean("quiet_hours", false)
        )
    )
    val notificationSettings: StateFlow<NotificationSettingsState> = _notificationSettings.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    private val serverManager = ServerManager(
        context = appContext,
        profileProvider = { profiles.value.find { it.id == _selectedServerId.value } },
        onLog = { newLog -> handleLog(newLog) },
        onStatusChange = { newStatus -> handleStatus(newStatus) }
    )

    private val dataService by lazy { LocalServerDataService { serverManager.getServerDir() } }

    init {
        viewModelScope.launch {
            profileRepository.loadProfiles()
            
            // If we have a selected ID but it's not in profiles, clear it
            val currentId = _selectedServerId.value
            if (currentId != null && profiles.value.none { it.id == currentId }) {
                _selectedServerId.value = profiles.value.firstOrNull()?.id
                prefs.edit().putString("selected_server_id", _selectedServerId.value).apply()
            }
            
            // If no ID selected but profiles exist, select the first one
            if (_selectedServerId.value == null && profiles.value.isNotEmpty()) {
                _selectedServerId.value = profiles.value.firstOrNull()?.id
                prefs.edit().putString("selected_server_id", _selectedServerId.value).apply()
            }

            val activeId = _selectedServerId.value
            val profile = profiles.value.find { it.id == activeId }
            if (profile != null) {
                _memoryMb.value = profile.memoryMb
                _activeTemplate.value = TemplateRegistry.ALL_TEMPLATES.find { it.id == profile.engineId } ?: initialTemplate
                serverManager.setTemplate(_activeTemplate.value)
            } else {
                serverManager.setTemplate(initialTemplate)
            }
            
            serverManager.setOnlineMode(_onlineMode.value)
            _status.value = serverManager.getCurrentStatus()
            updateIpAddress()
            serverManager.checkIntegrity()
            refreshAllLocalData()
            startMetricsLoop()
            startAutomationLoop()
            startDashboardUiStateLoop()
        }
    }

    fun selectServer(serverId: String) {
        if (_selectedServerId.value == serverId) return
        if (_status.value == ServerStatus.ONLINE || _status.value == ServerStatus.STARTING || _status.value == ServerStatus.PREPARING || _status.value == ServerStatus.DOWNLOADING) {
            showMessage("Stop the current server before switching profiles")
            return
        }
        
        _selectedServerId.value = serverId
        prefs.edit().putString("selected_server_id", serverId).apply()
        
        val profile = profiles.value.find { it.id == serverId }
        if (profile != null) {
            _memoryMb.value = profile.memoryMb
            _activeTemplate.value = TemplateRegistry.ALL_TEMPLATES.find { it.id == profile.engineId } ?: initialTemplate
            serverManager.switchProfile()
            serverManager.setTemplate(_activeTemplate.value)
            refreshAllLocalData()
            addActivity("Switched server", "Active profile: ${profile.name}", ActivityType.INFO)
        }
    }

    fun createServer(draft: ServerCreationDraft, iconUri: Uri? = null) {
        viewModelScope.launch {
            _operationInProgress.value = true
            
            // 3. Select an unused UDP port
            val usedPorts = profiles.value.map { it.port }.toSet()
            var port = draft.port
            while (usedPorts.contains(port)) {
                port++
            }
            
            val draftWithPort = draft.copy(port = port)
            
            val result = profileRepository.createProfile(draftWithPort)
            result.fold(
                onSuccess = { profile ->
                    // Copy icon if provided
                    var finalIconPath = profile.iconPath
                    if (iconUri != null) {
                        val serverDataService = LocalServerDataService { File(profile.serverDirectory) }
                        val iconResult = serverDataService.saveServerIcon(appContext.contentResolver, iconUri)
                        if (iconResult.success) {
                            finalIconPath = iconResult.message
                            profileRepository.updateProfile(profile.id, ServerProfileChanges(iconPath = finalIconPath))
                        }
                    }

                    // 5. Write server.properties into the new directory
                    val serverDataService = LocalServerDataService { File(profile.serverDirectory) }
                    val settings = ServerSettingsState(
                        serverName = profile.name,
                        levelName = profile.levelName,
                        port = profile.port,
                        memoryMb = profile.memoryMb,
                        maxPlayers = profile.maxPlayers
                    )
                    serverDataService.writeProperties(settings)
                    serverDataService.saveLocalServerProfile(settings, profile.engineId, finalIconPath)
                    
                    selectServer(profile.id)
                    _operationInProgress.value = false
                    showMessage("Server '${profile.name}' created")
                    addActivity("Server created", profile.name, ActivityType.SUCCESS)
                },
                onFailure = {
                    _operationInProgress.value = false
                    showMessage("Failed to create server: ${it.message}")
                }
            )
        }
    }

    fun updateServer(serverId: String, changes: ServerProfileChanges, iconUri: Uri? = null) {
        viewModelScope.launch {
            _operationInProgress.value = true
            
            val profile = profiles.value.find { it.id == serverId } ?: return@launch
            
            var updatedChanges = changes
            if (iconUri != null) {
                val serverDataService = LocalServerDataService { File(profile.serverDirectory) }
                val iconResult = serverDataService.saveServerIcon(appContext.contentResolver, iconUri)
                if (iconResult.success) {
                    updatedChanges = changes.copy(iconPath = iconResult.message)
                }
            }

            val result = profileRepository.updateProfile(serverId, updatedChanges)
            result.fold(
                onSuccess = { updated ->
                    // If the updated server is the active one, refresh settings
                    if (_selectedServerId.value == serverId) {
                        val serverDataService = LocalServerDataService { File(updated.serverDirectory) }
                        val currentSettings = serverDataService.readProperties()
                        val newSettings = currentSettings.copy(
                            serverName = updated.name,
                            levelName = updated.levelName,
                            port = updated.port,
                            memoryMb = updated.memoryMb,
                            maxPlayers = updated.maxPlayers
                        )
                        serverDataService.writeProperties(newSettings)
                        serverDataService.saveLocalServerProfile(newSettings, updated.engineId, updated.iconPath)
                        
                        _memoryMb.value = updated.memoryMb
                        _serverSettings.value = newSettings
                    }
                    _operationInProgress.value = false
                    showMessage("Server '${updated.name}' updated")
                },
                onFailure = {
                    _operationInProgress.value = false
                    showMessage("Failed to update server: ${it.message}")
                }
            )
        }
    }

    fun deleteServer(serverId: String, deleteFiles: Boolean = true) {
        viewModelScope.launch {
            if (_selectedServerId.value == serverId && _status.value != ServerStatus.STOPPED) {
                showMessage("Stop the server before deleting it")
                return@launch
            }
            
            val profile = profiles.value.find { it.id == serverId } ?: return@launch
            val result = profileRepository.deleteProfile(serverId, deleteFiles)
            result.fold(
                onSuccess = {
                    if (_selectedServerId.value == serverId) {
                        _selectedServerId.value = profiles.value.firstOrNull()?.id
                        prefs.edit().putString("selected_server_id", _selectedServerId.value).apply()
                        
                        val nextProfile = profiles.value.find { it.id == _selectedServerId.value }
                        if (nextProfile != null) {
                            _activeTemplate.value = TemplateRegistry.ALL_TEMPLATES.find { it.id == nextProfile.engineId } ?: initialTemplate
                            serverManager.switchProfile()
                            serverManager.setTemplate(_activeTemplate.value)
                        } else {
                            serverManager.switchProfile()
                        }
                        refreshAllLocalData()
                    }
                    showMessage("Server '${profile.name}' deleted")
                },
                onFailure = {
                    showMessage("Failed to delete server: ${it.message}")
                }
            )
        }
    }

    fun startServer() {
        if (_status.value == ServerStatus.ONLINE || _status.value == ServerStatus.STARTING || _status.value == ServerStatus.PREPARING || _status.value == ServerStatus.DOWNLOADING) return
        manualStopRequested = false
        addActivity("Server start requested", _activeTemplate.value.name, ActivityType.INFO)
        serverManager.startServer(_memoryMb.value, viewModelScope)
    }

    fun stopServer() {
        if (_status.value == ServerStatus.STOPPED || _status.value == ServerStatus.STOPPING) return
        manualStopRequested = true
        autoRestartJob?.cancel()
        addActivity("Server stop requested", "Stopping the local server safely", ActivityType.WARNING)
        serverManager.stopServer()
    }

    fun restartServer() {
        if (_status.value != ServerStatus.ONLINE) {
            showMessage("The server must be running before it can restart")
            return
        }
        addActivity("Server restart requested", "Restarting with ${_memoryMb.value} MB", ActivityType.WARNING)
        serverManager.restartServer()
    }

    fun sendCommand(command: String) {
        val clean = command.trim().removePrefix("/")
        if (clean.isBlank()) return
        if (_status.value != ServerStatus.ONLINE) {
            showMessage("Start the server before sending commands")
            return
        }
        serverManager.sendCommand(clean)
        addActivity("Console command sent", "/$clean", ActivityType.INFO)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        showMessage("Console cleared")
    }

    fun updateIpAddress() {
        _ipAddress.value = NetworkUtils.getLocalIpAddress()
    }

    fun setMemoryMb(memory: Int) {
        val safe = memory.coerceIn(384, 8192)
        _memoryMb.value = safe
        _serverSettings.value = _serverSettings.value.copy(memoryMb = safe)
        prefs.edit().putInt("memory_mb", safe).apply()
    }

    fun setTemplate(template: ServerTemplate) {
        if (_status.value == ServerStatus.ONLINE || _status.value == ServerStatus.STARTING || _status.value == ServerStatus.PREPARING || _status.value == ServerStatus.DOWNLOADING) {
            showMessage("Stop the server before changing its engine")
            return
        }
        _activeTemplate.value = template
        prefs.edit().putString("template_id", template.id).apply()
        serverManager.setTemplate(template)
        serverManager.setOnlineMode(_onlineMode.value)
        refreshAllLocalData()
        addActivity("Server engine selected", template.name, ActivityType.SUCCESS)
    }

    fun setOnlineMode(enabled: Boolean) {
        _onlineMode.value = enabled
        prefs.edit().putBoolean("online_mode", enabled).apply()
        serverManager.setOnlineMode(enabled)
        _serverSettings.value = _serverSettings.value.copy(onlineMode = enabled)
    }

    fun refreshAllLocalData() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = dataService.readProperties().copy(
                memoryMb = _memoryMb.value,
                onlineMode = _onlineMode.value,
                autoRestart = prefs.getBoolean("auto_restart", true),
                autoBackup = prefs.getBoolean("auto_backup", false)
            )
            val files = toFileInfo(dataService.list(""), "")
            val plugins = dataService.listPlugins()
            val backups = dataService.listBackups()
            val worlds = dataService.listWorlds(settings.levelName)
            withContext(Dispatchers.Main) {
                _serverSettings.value = settings
                _currentFiles.value = files
                _plugins.value = plugins
                _backups.value = backups
                _worlds.value = worlds
                refreshHealthAndRecommendations()
            }
        }
    }

    fun navigateToFolder(relativePath: String) {
        _currentPath.value = relativePath
        refreshFiles()
    }

    fun navigateUp() {
        val path = _currentPath.value
        if (path.isBlank()) return
        _currentPath.value = path.substringBeforeLast('/', "")
        refreshFiles()
    }

    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val path = _currentPath.value
            val files = toFileInfo(dataService.list(path), path)
            withContext(Dispatchers.Main) { _currentFiles.value = files }
        }
    }

    fun openTextFile(relativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = dataService.readText(relativePath)
            withContext(Dispatchers.Main) {
                if (result.success) {
                    _selectedFilePath.value = relativePath
                    _selectedFileContent.value = result.message
                } else showMessage(result.message)
            }
        }
    }

    fun closeTextFile() {
        _selectedFilePath.value = null
        _selectedFileContent.value = null
    }

    fun saveTextFile(content: String) {
        val path = _selectedFilePath.value ?: return
        runOperation({ dataService.writeText(path, content) }) { refreshFiles() }
    }

    fun createFolder(name: String) = runOperation({ dataService.createFolder(_currentPath.value, name) }) { refreshFiles() }

    fun createFile(name: String) = runOperation({ dataService.createFile(_currentPath.value, name) }) { refreshFiles() }

    fun renamePath(relativePath: String, newName: String) = runOperation({ dataService.rename(relativePath, newName) }) { refreshFiles() }

    fun deletePath(relativePath: String) = runOperation({ dataService.delete(relativePath) }) {
        closeTextFile()
        refreshFiles()
        refreshPlugins()
        refreshBackups()
        refreshWorlds()
    }

    fun importFile(uri: Uri) = runOperation({ dataService.importDocument(appContext.contentResolver, uri, _currentPath.value) }) { refreshFiles() }


    /** Compatibility hook retained for the legacy file browser. */
    fun closeFileManager() {
        _currentPath.value = ""
        closeTextFile()
        refreshFiles()
    }

    fun refreshPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            val value = dataService.listPlugins()
            withContext(Dispatchers.Main) { _plugins.value = value }
        }
    }

    fun importPlugin(uri: Uri) = runOperation({ dataService.importPlugin(appContext.contentResolver, uri) }) { refreshPlugins() }

    fun togglePlugin(plugin: PluginEntry, enable: Boolean) = runOperation({ dataService.togglePlugin(plugin.fileName, enable) }) { refreshPlugins() }

    fun refreshBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val value = dataService.listBackups()
            withContext(Dispatchers.Main) { _backups.value = value }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            if (_operationInProgress.value) return@launch
            _operationInProgress.value = true
            if (_status.value == ServerStatus.ONLINE) {
                serverManager.sendCommand("save-all")
                delay(1200)
            }
            val result = withContext(Dispatchers.IO) { dataService.createBackup() }
            _operationInProgress.value = false
            if (result.success) {
                uiPrefs.edit().putLong("last_auto_backup_ms", System.currentTimeMillis()).apply()
                refreshBackups()
                addActivity("Backup completed", "A local server backup was created", ActivityType.SUCCESS)
                notificationManager.notifyBackupCompleted(result.message, _notificationSettings.value)
            }
            showMessage(result.message)
        }
    }

    fun restoreBackup(backup: BackupEntry) {
        if (_status.value != ServerStatus.STOPPED && _status.value != ServerStatus.FAILED && _status.value != ServerStatus.CRASHED) {
            showMessage("Stop the server before restoring a backup")
            return
        }
        runOperation({ dataService.restoreBackup(backup.fileName) }) {
            refreshAllLocalData()
            addActivity("Backup restored", backup.fileName, ActivityType.WARNING)
        }
    }

    fun deleteBackup(backup: BackupEntry) = runOperation({ dataService.deleteBackup(backup.fileName) }) { refreshBackups() }

    fun refreshWorlds() {
        viewModelScope.launch(Dispatchers.IO) {
            val value = dataService.listWorlds(_serverSettings.value.levelName)
            withContext(Dispatchers.Main) { _worlds.value = value }
        }
    }

    fun importWorld(uri: Uri) = runOperation({ dataService.importWorldZip(appContext.contentResolver, uri) }) { refreshWorlds() }

    fun activateWorld(world: WorldEntry) {
        if (_status.value == ServerStatus.ONLINE || _status.value == ServerStatus.STARTING || _status.value == ServerStatus.PREPARING || _status.value == ServerStatus.DOWNLOADING) {
            showMessage("Stop the server before switching worlds")
            return
        }
        saveServerSettings(_serverSettings.value.copy(levelName = world.name))
    }

    fun updateServerSettingsDraft(settings: ServerSettingsState) {
        _serverSettings.value = settings
    }

    fun saveServerSettings(settings: ServerSettingsState = _serverSettings.value) {
        if (_status.value == ServerStatus.ONLINE || _status.value == ServerStatus.STARTING || _status.value == ServerStatus.PREPARING || _status.value == ServerStatus.DOWNLOADING) {
            showMessage("Stop the server before applying settings")
            return
        }
        val safe = settings.copy(
            maxPlayers = settings.maxPlayers.coerceIn(1, 100),
            viewDistance = settings.viewDistance.coerceIn(2, 32),
            port = settings.port.coerceIn(1024, 65535),
            memoryMb = settings.memoryMb.coerceIn(384, 8192)
        )
        
        val currentId = _selectedServerId.value
        if (currentId != null) {
            updateServer(
                currentId,
                ServerProfileChanges(
                    name = safe.serverName,
                    memoryMb = safe.memoryMb,
                    maxPlayers = safe.maxPlayers,
                    port = safe.port,
                    levelName = safe.levelName
                )
            )
        } else {
            runOperation({
                dataService.writeProperties(safe)
            }) {
                setMemoryMb(safe.memoryMb)
                setOnlineMode(safe.onlineMode)
                prefs.edit()
                    .putBoolean("auto_restart", safe.autoRestart)
                    .putBoolean("auto_backup", safe.autoBackup)
                    .apply()
                if (safe.autoBackup && !uiPrefs.contains("last_auto_backup_ms")) {
                    uiPrefs.edit().putLong("last_auto_backup_ms", System.currentTimeMillis()).apply()
                }
                _serverSettings.value = safe
                refreshAllLocalData()
                addActivity("Server settings saved", "Changes will apply on the next start", ActivityType.SUCCESS)
            }
        }
    }

    fun applySafeOptimization() {
        if (_status.value == ServerStatus.ONLINE || _status.value == ServerStatus.STARTING || _status.value == ServerStatus.PREPARING || _status.value == ServerStatus.DOWNLOADING) {
            showMessage("Stop the server before applying optimizations")
            return
        }
        viewModelScope.launch {
            _operationInProgress.value = true
            val backup = withContext(Dispatchers.IO) { dataService.createBackup() }
            if (!backup.success) {
                _operationInProgress.value = false
                showMessage("Optimization cancelled because backup failed: ${backup.message}")
                return@launch
            }
            val current = _serverSettings.value
            val optimized = current.copy(
                viewDistance = current.viewDistance.coerceAtMost(8),
                maxPlayers = current.maxPlayers.coerceAtMost(50),
                autoRestart = true
            )
            val result = withContext(Dispatchers.IO) { dataService.writeProperties(optimized) }
            _operationInProgress.value = false
            if (result.success) {
                _serverSettings.value = optimized
                prefs.edit().putBoolean("auto_restart", true).apply()
                refreshBackups()
                refreshHealthAndRecommendations()
                addActivity("Safe optimization applied", "Backup created before updating server.properties", ActivityType.SUCCESS)
            }
            showMessage(result.message)
        }
    }

    fun kickPlayer(name: String) = sendCommand("kick $name")
    fun banPlayer(name: String) = sendCommand("ban $name")
    fun whitelistPlayer(name: String) = sendCommand("whitelist add $name")

    fun askAssistant(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        val userMessage = AssistantMessage(UUID.randomUUID().toString(), true, clean)
        _assistantMessages.value = _assistantMessages.value + userMessage
        val reply = buildAssistantReply(clean)
        _assistantMessages.value = _assistantMessages.value + AssistantMessage(UUID.randomUUID().toString(), false, reply)
    }

    fun clearAssistantChat() {
        _assistantMessages.value = emptyList()
    }

    fun updateAppSettings(value: AppSettingsState) {
        _appSettings.value = value
        uiPrefs.edit()
            .putBoolean("dark_mode", value.darkMode)
            .putString("language", value.language)
            .putString("startup_screen", value.startupScreen)
            .putBoolean("data_saver", value.dataSaver)
            .putBoolean("haptic", value.hapticFeedback)
            .putBoolean("confirm_destructive", value.confirmDestructiveActions)
            .apply()
    }

    fun updateNotificationSettings(value: NotificationSettingsState) {
        _notificationSettings.value = value
        uiPrefs.edit()
            .putBoolean("notify_offline", value.serverOfflineAlerts)
            .putBoolean("notify_crash", value.crashAlerts)
            .putBoolean("notify_backup", value.backupReminders)
            .putBoolean("notify_plugins", value.pluginUpdateAlerts)
            .putBoolean("notify_marketplace", value.marketplaceAnnouncements)
            .putBoolean("notify_sound", value.sound)
            .putBoolean("notify_vibration", value.vibration)
            .putBoolean("quiet_hours", value.quietHoursEnabled)
            .apply()
    }

    fun consumeOperationMessage() {
        _operationMessage.value = null
    }

    private fun handleStatus(newStatus: ServerStatus) {
        val previous = _status.value
        _status.value = newStatus
        
        // Reset metrics on stop or start
        if (newStatus == ServerStatus.STOPPED || newStatus == ServerStatus.STARTING || newStatus == ServerStatus.PREPARING) {
            _players.value = emptyList()
            _metrics.value = RuntimeMetrics()
        }

        when (newStatus) {
            ServerStatus.ONLINE -> {
                autoRestartJob?.cancel()
                autoRestartJob = null
                autoRestartAttempts = 0
                manualStopRequested = false
                addActivity("Server online", _activeTemplate.value.name, ActivityType.SUCCESS)
            }
            ServerStatus.STOPPED -> {
                if (previous != ServerStatus.STOPPED) {
                    addActivity("Server stopped", "Local server is offline", ActivityType.INFO)
                    if (previous == ServerStatus.ONLINE && !manualStopRequested) {
                        notificationManager.notifyServerProblem(
                            title = "MineHost server went offline",
                            message = "The local Bedrock server stopped unexpectedly.",
                            settings = _notificationSettings.value,
                            critical = false
                        )
                    }
                }
                manualStopRequested = false
            }
            ServerStatus.CRASHED, ServerStatus.FAILED -> {
                addActivity("Server problem detected", newStatus.name, ActivityType.ERROR)
                addCrash("Server ${newStatus.name.lowercase()}", _logs.value.takeLast(20).joinToString("\n"), CrashSeverity.CRITICAL)
                notificationManager.notifyServerProblem(
                    title = "MineHost server ${newStatus.name.lowercase()}",
                    message = _logs.value.takeLast(3).joinToString(" ").take(500).ifBlank { "Open Crash Analysis for details." },
                    settings = _notificationSettings.value,
                    critical = true
                )
                scheduleAutoRestart()
            }
            else -> Unit
        }
        refreshHealthAndRecommendations()
    }

    private fun handleLog(newLog: String) {
        viewModelScope.launch {
            _logs.value = (_logs.value + newLog).takeLast(1000)
            parsePlayers(newLog)
            parseTps(newLog)
            parseCrash(newLog)
        }
    }

    private fun parsePlayers(line: String) {
        val join = Regex("([A-Za-z0-9_]{3,24})\\s+(?:joined the game|joined the server|logged in)", RegexOption.IGNORE_CASE).find(line)
        if (join != null) {
            val name = join.groupValues[1]
            val now = System.currentTimeMillis()
            _players.value = (_players.value.filterNot { it.name.equals(name, true) } + PlayerSession(name, now, now, true)).sortedBy { it.name.lowercase() }
            addActivity("$name joined", "Player connected to the local server", ActivityType.SUCCESS)
            return
        }
        val leave = Regex("([A-Za-z0-9_]{3,24})\\s+(?:left the game|left the server|disconnected|logged out)", RegexOption.IGNORE_CASE).find(line)
        if (leave != null) {
            val name = leave.groupValues[1]
            _players.value = _players.value.filterNot { it.name.equals(name, true) }
            addActivity("$name left", "Player disconnected", ActivityType.INFO)
        }
    }

    private fun parseTps(line: String) {
        val match = Regex("(?:TPS|tps)[^0-9]*(\\d{1,2}(?:\\.\\d+)?)").find(line)
        val value = match?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: return
        val safeTps = value.coerceIn(0f, 20f)
        _metrics.value = _metrics.value.copy(tps = safeTps)
    }

    private fun parseCrash(line: String) {
        val lower = line.lowercase()
        val severity = when {
            "fatal" in lower || "outofmemory" in lower || "crash" in lower -> CrashSeverity.CRITICAL
            "exception" in lower || "[error]" in lower || " error" in lower -> CrashSeverity.ERROR
            "[warn]" in lower || " warning" in lower -> CrashSeverity.WARNING
            else -> null
        } ?: return
        if (severity != CrashSeverity.WARNING) addCrash("Runtime ${severity.name.lowercase()}", line, severity)
    }

    private fun addCrash(title: String, details: String, severity: CrashSeverity) {
        val entry = CrashEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), title, details.take(4000), severity)
        _crashes.value = (listOf(entry) + _crashes.value).take(50)
        refreshHealthAndRecommendations()
    }

    private fun addActivity(title: String, description: String, type: ActivityType) {
        val entry = ActivityEvent(UUID.randomUUID().toString(), System.currentTimeMillis(), title, description, type)
        _activities.value = (listOf(entry) + _activities.value).take(100)
    }

    private fun refreshHealthAndRecommendations() {
        val warnings = mutableListOf<String>()
        val status = _status.value
        val recentErrors = _crashes.value.count { System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000L && it.severity != CrashSeverity.WARNING }
        val metrics = _metrics.value
        val tps = metrics.tps
        
        // Critical alerts (Only if actively bad)
        if (status == ServerStatus.CRASHED) {
            warnings += "CRITICAL: Server crashed and is offline."
        } else if (status == ServerStatus.FAILED) {
            warnings += "Startup failed. Inspect Java/Engine logs."
        }
        
        // Current status warnings
        if (status == ServerStatus.ONLINE) {
            if (tps != null && tps < 16.0) warnings += "Server lag detected (TPS < 16)."
            if (metrics.cpuPercent != null && metrics.cpuPercent > 95) warnings += "Maximum CPU load detected."
        }
        
        // Historical and Configuration warnings
        if (recentErrors > 0 && status != ServerStatus.CRASHED) {
            warnings += "Recovered from $recentErrors crash(es) today."
        }
        if (_serverSettings.value.viewDistance > 12) {
             warnings += "View distance is set high (${_serverSettings.value.viewDistance})."
        }
        if (_memoryMb.value < 768) warnings += "Allocated memory is low (< 768MB)."
        
        val score = when (status) {
            ServerStatus.ONLINE -> 95
            ServerStatus.STARTING, ServerStatus.PREPARING, ServerStatus.DOWNLOADING -> 85
            ServerStatus.STOPPING -> 80
            ServerStatus.STOPPED -> 75
            ServerStatus.FAILED, ServerStatus.CRASHED -> 30
        } - (warnings.size * 5)
        
        _health.value = HealthSnapshot(status, score.coerceIn(0, 100), warnings.distinct())
        
        _recommendations.value = buildList {
            if (_serverSettings.value.viewDistance > 8) add("Reduce view distance to 8 for smoother mobile hosting.")
            if (_memoryMb.value < 1024) add("Consider allocating at least 1024 MB for plugin-heavy worlds.")
            if (_plugins.value.count { it.enabled } > 8) add("Review enabled plugins and remove anything unused.")
            if (_backups.value.isEmpty()) add("Create a backup before changing engines or server settings.")
            if (recentErrors > 0 && status == ServerStatus.ONLINE) add("Review Crash Analysis to prevent future downtime.")
            if (isEmpty()) add("No urgent optimization is currently suggested.")
        }
    }

    private fun startMetricsLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val pid = serverManager.getProcessId()
                // On some Android versions, we can't see other processes in /proc
                // If we have a PID and the status is Online, we should try to read metrics anyway
                val isProcessAlive = pid != null && (File("/proc/$pid").exists() || _status.value == ServerStatus.ONLINE)
                
                val (cpu, ram) = if (isProcessAlive && pid != null) metricsReader.read(pid) else null to null
                val root = runCatching { serverManager.getServerDir() }.getOrNull()
                val dataSize = root?.let { dataService.directorySize(it) } ?: 0L
                val available = root?.usableSpace ?: 0L
                val startedAt = serverManager.getStartedAtMillis()
                
                val isOnline = _status.value == ServerStatus.ONLINE
                
                val snapshot = _metrics.value.copy(
                    cpuPercent = if (isProcessAlive) cpu else null,
                    ramBytes = if (isProcessAlive) ram else null,
                    serverDataBytes = dataSize,
                    availableStorageBytes = available,
                    playersOnline = if (isOnline) _players.value.size else null,
                    uptimeMillis = if (isOnline) startedAt?.let { (System.currentTimeMillis() - it).coerceAtLeast(0) } else null,
                    processId = pid,
                    isPlayerTrackingAvailable = isOnline
                )
                withContext(Dispatchers.Main) {
                    _metrics.value = snapshot
                    refreshHealthAndRecommendations()
                }
                delay(if (isProcessAlive) 1500 else 3000)
            }
        }
    }

    private fun startDashboardUiStateLoop() {
        viewModelScope.launch {
            while (isActive) {
                val metrics = _metrics.value
                val status = _status.value
                val currentProfiles = profiles.value
                val selectedId = _selectedServerId.value
                val hasProfile = selectedId != null
                val isOnline = status == ServerStatus.ONLINE
                
                _dashboardUiState.value = DashboardUiState(
                    hasServerProfile = hasProfile,
                    serverStatus = status,
                    isProcessAlive = metrics.processId != null && File("/proc/${metrics.processId}").exists(),
                    isServerReady = isOnline,
                    totalServerCount = currentProfiles.size,
                    onlineServerCount = if (isOnline) 1 else 0,
                    activePlayers = if (isOnline) metrics.playersOnline else null,
                    ramUsedBytes = metrics.ramBytes,
                    cpuUsagePercent = metrics.cpuPercent,
                    tps = if (isOnline) metrics.tps else null,
                    pingMs = null,
                    healthSummary = _health.value,
                    uptimeMillis = if (isOnline) metrics.uptimeMillis else null,
                    latestMetricTimestamp = System.currentTimeMillis()
                )
                delay(1000)
            }
        }
    }

    private fun scheduleAutoRestart() {
        if (!prefs.getBoolean("auto_restart", true)) return
        if (manualStopRequested || autoRestartAttempts >= 3 || autoRestartJob?.isActive == true) return
        autoRestartAttempts += 1
        val attempt = autoRestartAttempts
        autoRestartJob = viewModelScope.launch {
            addActivity("Auto-restart scheduled", "Attempt $attempt of 3 in 5 seconds", ActivityType.WARNING)
            delay(5000)
            if (_status.value == ServerStatus.FAILED || _status.value == ServerStatus.CRASHED) {
                addActivity("Auto-restart attempt", "Starting the local server again", ActivityType.INFO)
                serverManager.startServer(_memoryMb.value, viewModelScope)
            }
        }
    }

    private fun startAutomationLoop() {
        if (!uiPrefs.contains("last_auto_backup_ms")) {
            uiPrefs.edit().putLong("last_auto_backup_ms", System.currentTimeMillis()).apply()
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15 * 60 * 1000L)
                if (!prefs.getBoolean("auto_backup", false)) continue
                val last = uiPrefs.getLong("last_auto_backup_ms", 0L)
                val now = System.currentTimeMillis()
                if (now - last < 6 * 60 * 60 * 1000L) continue
                val root = runCatching { serverManager.getServerDir() }.getOrNull() ?: continue
                if (!root.exists()) continue
                if (_status.value == ServerStatus.ONLINE) {
                    serverManager.sendCommand("save-all")
                    delay(1200)
                }
                val result = dataService.createBackup()
                if (result.success) {
                    uiPrefs.edit().putLong("last_auto_backup_ms", now).apply()
                    withContext(Dispatchers.Main) {
                        refreshBackups()
                        addActivity("Automatic backup completed", result.message, ActivityType.SUCCESS)
                        notificationManager.notifyBackupCompleted(result.message, _notificationSettings.value)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        addActivity("Automatic backup failed", result.message, ActivityType.ERROR)
                    }
                }
            }
        }
    }

    private fun buildAssistantReply(prompt: String): String {
        val lower = prompt.lowercase()
        val errors = _crashes.value.take(3)
        val metrics = _metrics.value
        return when {
            "crash" in lower || "error" in lower -> {
                if (errors.isEmpty()) "I did not find a recorded crash in this app session. Check the Console after the next failure so I can summarize the exact error lines."
                else "I found ${errors.size} recent error event(s). The newest is: ${errors.first().details.take(240)}"
            }
            "lag" in lower || "tps" in lower || "performance" in lower -> {
                val tpsText = metrics.tps?.let { "Current parsed TPS is ${"%.1f".format(it)}. " } ?: "The server has not reported a TPS value yet. "
                tpsText + _recommendations.value.joinToString(" ")
            }
            "player" in lower -> "I currently see ${_players.value.size} player(s) based on real join and leave messages in the console."
            "memory" in lower || "ram" in lower -> {
                val used = metrics.ramBytes?.let(::formatBytes) ?: "unavailable"
                "The server is configured for ${_memoryMb.value} MB. Current process memory is $used."
            }
            "log" in lower -> {
                val important = _logs.value.filter { it.contains("warn", true) || it.contains("error", true) || it.contains("exception", true) }.takeLast(5)
                if (important.isEmpty()) "I did not find warning or error lines in the current console buffer."
                else "Important recent log lines:\n" + important.joinToString("\n")
            }
            "optimiz" in lower -> "The safe optimization profile creates a backup, limits view distance to 8, keeps max players at 50 or lower, and enables automatic restart. Open Auto Optimization to review and apply it."
            else -> "I can answer using the local server's real status, logs, settings, players, backups, and metrics. Try asking: “Why is my server lagging?”, “Explain recent errors”, or “How much memory is used?”"
        }
    }

    private fun toFileInfo(files: List<File>, path: String): List<FileInfo> = files.map { file ->
        val relative = if (path.isBlank()) file.name else "$path/${file.name}"
        FileInfo(file.name, if (file.isDirectory) 0L else file.length(), file.isDirectory, file.lastModified(), relative)
    }

    private fun runOperation(block: suspend () -> OperationResult, afterSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _operationInProgress.value = true
            val result = withContext(Dispatchers.IO) { block() }
            _operationInProgress.value = false
            if (result.success) afterSuccess()
            showMessage(result.message)
        }
    }

    private fun showMessage(message: String) {
        _operationMessage.value = message
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
