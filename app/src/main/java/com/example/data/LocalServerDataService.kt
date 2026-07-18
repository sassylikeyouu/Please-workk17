package com.example.data

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LocalServerDataService(private val rootProvider: () -> File) {
    private val root: File get() = rootProvider().apply { mkdirs() }

    private fun resolve(relativePath: String): File? {
        return runCatching {
            val base = root.canonicalFile
            val file = File(base, relativePath).canonicalFile
            if (file.path == base.path || file.path.startsWith(base.path + File.separator)) file else null
        }.getOrNull()
    }

    fun list(relativePath: String = ""): List<File> =
        resolve(relativePath)?.takeIf { it.isDirectory }?.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

    fun readText(relativePath: String, maxBytes: Long = 2L * 1024 * 1024): OperationResult {
        val file = resolve(relativePath) ?: return OperationResult(false, "Unsafe file path")
        if (!file.isFile) return OperationResult(false, "File does not exist")
        if (file.length() > maxBytes) return OperationResult(false, "File is too large to edit")
        return runCatching { OperationResult(true, file.readText()) }
            .getOrElse { OperationResult(false, it.message ?: "Unable to read file") }
    }

    fun writeText(relativePath: String, content: String): OperationResult {
        val file = resolve(relativePath) ?: return OperationResult(false, "Unsafe file path")
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(content)
            OperationResult(true, "Saved ${file.name}")
        }.getOrElse { OperationResult(false, it.message ?: "Unable to save file") }
    }

    fun createFolder(parentPath: String, name: String): OperationResult {
        val cleanName = name.trim().replace('/', '_').replace('\\', '_')
        if (cleanName.isBlank()) return OperationResult(false, "Enter a folder name")
        val folder = resolve(if (parentPath.isBlank()) cleanName else "$parentPath/$cleanName")
            ?: return OperationResult(false, "Unsafe folder path")
        return if (folder.mkdirs()) OperationResult(true, "Folder created")
        else OperationResult(false, if (folder.exists()) "Folder already exists" else "Unable to create folder")
    }

    fun createFile(parentPath: String, name: String): OperationResult {
        val cleanName = name.trim().replace('/', '_').replace('\\', '_')
        if (cleanName.isBlank()) return OperationResult(false, "Enter a file name")
        val file = resolve(if (parentPath.isBlank()) cleanName else "$parentPath/$cleanName")
            ?: return OperationResult(false, "Unsafe file path")
        return runCatching {
            file.parentFile?.mkdirs()
            if (!file.createNewFile()) return OperationResult(false, "File already exists")
            OperationResult(true, "File created")
        }.getOrElse { OperationResult(false, it.message ?: "Unable to create file") }
    }

    fun rename(relativePath: String, newName: String): OperationResult {
        val source = resolve(relativePath) ?: return OperationResult(false, "Unsafe file path")
        val cleanName = newName.trim().replace('/', '_').replace('\\', '_')
        if (cleanName.isBlank()) return OperationResult(false, "Enter a new name")
        val target = File(source.parentFile, cleanName)
        if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
            return OperationResult(false, "Unsafe destination")
        }
        return if (source.renameTo(target)) OperationResult(true, "Renamed to $cleanName")
        else OperationResult(false, "Unable to rename")
    }

    fun delete(relativePath: String): OperationResult {
        val target = resolve(relativePath) ?: return OperationResult(false, "Unsafe file path")
        if (target.canonicalPath == root.canonicalPath) return OperationResult(false, "Cannot delete server root")
        val ok = if (target.isDirectory) target.deleteRecursively() else target.delete()
        return OperationResult(ok, if (ok) "Deleted ${target.name}" else "Unable to delete ${target.name}")
    }

    fun importDocument(resolver: ContentResolver, uri: Uri, parentPath: String): OperationResult {
        val name = queryDisplayName(resolver, uri) ?: "imported_file"
        val target = resolve(if (parentPath.isBlank()) name else "$parentPath/$name")
            ?: return OperationResult(false, "Unsafe import destination")
        return runCatching {
            target.parentFile?.mkdirs()
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return OperationResult(false, "Unable to open selected file")
            OperationResult(true, "Imported $name")
        }.getOrElse { OperationResult(false, it.message ?: "Import failed") }
    }

    fun listPlugins(): List<PluginEntry> {
        val dir = File(root, "plugins").apply { mkdirs() }
        return dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".jar", true) || it.name.endsWith(".jar.disabled", true)) }
            ?.sortedBy { it.name.lowercase() }
            ?.map {
                val enabled = it.name.endsWith(".jar", true)
                val base = it.name.removeSuffix(".disabled").removeSuffix(".jar")
                PluginEntry(base, it.name, it.length(), enabled, it.lastModified())
            } ?: emptyList()
    }

    fun togglePlugin(fileName: String, enable: Boolean): OperationResult {
        val pluginDir = File(root, "plugins").apply { mkdirs() }
        val source = File(pluginDir, fileName)
        if (!source.exists()) return OperationResult(false, "Plugin file not found")
        val targetName = when {
            enable && source.name.endsWith(".disabled") -> source.name.removeSuffix(".disabled")
            !enable && !source.name.endsWith(".disabled") -> source.name + ".disabled"
            else -> source.name
        }
        if (targetName == source.name) return OperationResult(true, "No change required")
        val ok = source.renameTo(File(pluginDir, targetName))
        return OperationResult(ok, if (ok) "Plugin ${if (enable) "enabled" else "disabled"}; restart required" else "Unable to update plugin")
    }

    fun importPlugin(resolver: ContentResolver, uri: Uri): OperationResult {
        val displayName = queryDisplayName(resolver, uri) ?: "plugin.jar"
        if (!displayName.endsWith(".jar", true)) return OperationResult(false, "Select a .jar plugin file")
        val dir = File(root, "plugins").apply { mkdirs() }
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(File(dir, displayName)).use { output -> input.copyTo(output) }
            } ?: return OperationResult(false, "Unable to open selected plugin")
            OperationResult(true, "Imported $displayName; restart the server to load it")
        }.getOrElse { OperationResult(false, it.message ?: "Plugin import failed") }
    }

    fun readProperties(): ServerSettingsState {
        val file = File(root, "server.properties")
        val p = Properties()
        if (file.exists()) runCatching { file.inputStream().use(p::load) }
        return ServerSettingsState(
            serverName = p.getProperty("motd", "Local Bedrock Server").ifBlank { "Local Bedrock Server" },
            gameMode = p.getProperty("gamemode", "survival"),
            difficulty = p.getProperty("difficulty", "normal"),
            maxPlayers = p.getProperty("max-players", "10").toIntOrNull()?.coerceIn(1, 100) ?: 10,
            whitelistEnabled = p.getProperty("white-list", "off").equals("on", true) || p.getProperty("white-list", "false").toBoolean(),
            onlineMode = p.getProperty("xbox-auth", "on").equals("on", true) || p.getProperty("xbox-auth", "true").toBoolean(),
            viewDistance = p.getProperty("view-distance", "8").toIntOrNull()?.coerceIn(2, 32) ?: 8,
            port = p.getProperty("server-port", "19132").toIntOrNull()?.coerceIn(1024, 65535) ?: 19132,
            levelName = p.getProperty("level-name", "world").ifBlank { "world" }
        )
    }

    fun writeProperties(settings: ServerSettingsState): OperationResult {
        val file = File(root, "server.properties")
        val p = Properties()
        if (file.exists()) runCatching { file.inputStream().use(p::load) }
        p["motd"] = settings.serverName
        p["gamemode"] = settings.gameMode
        p["difficulty"] = settings.difficulty
        p["max-players"] = settings.maxPlayers.toString()
        p["white-list"] = if (settings.whitelistEnabled) "on" else "off"
        p["xbox-auth"] = if (settings.onlineMode) "on" else "off"
        p["view-distance"] = settings.viewDistance.toString()
        p["server-port"] = settings.port.toString()
        p["server-ip"] = "0.0.0.0"
        p["level-name"] = settings.levelName
        return runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { p.store(it, "MineHost server settings") }
            OperationResult(true, "Server settings saved")
        }.getOrElse { OperationResult(false, it.message ?: "Unable to save settings") }
    }

    fun listBackups(): List<BackupEntry> {
        val dir = File(root, "backups").apply { mkdirs() }
        return dir.listFiles()?.filter { it.isFile && it.extension.equals("zip", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupEntry(it.name, it.length(), it.lastModified(), it.absolutePath) }
            ?: emptyList()
    }

    fun createBackup(): OperationResult {
        val backupDir = File(root, "backups").apply { mkdirs() }
        val file = File(backupDir, "minehost-${System.currentTimeMillis()}.zip")
        return runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
                root.listFiles()?.filter { it.name != "backups" && it.name != "runtime" && !it.name.endsWith(".jar", true) }
                    ?.forEach { addToZip(zip, it, it.name) }
            }
            OperationResult(true, "Backup created: ${file.name}")
        }.getOrElse {
            file.delete()
            OperationResult(false, it.message ?: "Backup failed")
        }
    }

    fun restoreBackup(fileName: String): OperationResult {
        val backup = File(File(root, "backups"), fileName)
        if (!backup.isFile) return OperationResult(false, "Backup not found")
        return runCatching {
            ZipInputStream(BufferedInputStream(FileInputStream(backup))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(root, entry.name).canonicalFile
                    if (!target.path.startsWith(root.canonicalPath + File.separator)) {
                        throw SecurityException("Unsafe archive entry")
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            OperationResult(true, "Backup restored; start the server to verify it")
        }.getOrElse { OperationResult(false, it.message ?: "Restore failed") }
    }

    fun deleteBackup(fileName: String): OperationResult {
        val file = File(File(root, "backups"), fileName)
        val safe = runCatching { file.canonicalPath.startsWith(File(root, "backups").canonicalPath + File.separator) }.getOrDefault(false)
        if (!safe || !file.exists()) return OperationResult(false, "Backup not found")
        return OperationResult(file.delete(), if (!file.exists()) "Backup deleted" else "Unable to delete backup")
    }

    fun listWorlds(activeLevelName: String): List<WorldEntry> {
        val candidates = buildList {
            File(root, "worlds").takeIf { it.isDirectory }?.listFiles()?.filter { it.isDirectory }?.let(::addAll)
            root.listFiles()?.filter { it.isDirectory && (it.name.equals("world", true) || it.name.startsWith("world_") || it.name.equals(activeLevelName, true)) }?.let(::addAll)
        }.distinctBy { it.canonicalPath }
        return candidates.sortedBy { it.name.lowercase() }.map {
            WorldEntry(it.name, it.absolutePath, directorySize(it), it.lastModified(), it.name.equals(activeLevelName, true))
        }
    }

    fun importWorldZip(resolver: ContentResolver, uri: Uri): OperationResult {
        val displayName = queryDisplayName(resolver, uri) ?: "world.zip"
        if (!displayName.endsWith(".zip", true)) return OperationResult(false, "Select a .zip world archive")
        val worldName = displayName.removeSuffix(".zip").replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "imported_world" }
        val destination = File(root, "worlds/$worldName").apply { mkdirs() }
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val target = File(destination, entry.name).canonicalFile
                        if (!target.path.startsWith(destination.canonicalPath + File.separator)) throw SecurityException("Unsafe archive entry")
                        if (entry.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return OperationResult(false, "Unable to open selected archive")
            OperationResult(true, "World imported as $worldName")
        }.getOrElse {
            destination.deleteRecursively()
            OperationResult(false, it.message ?: "World import failed")
        }
    }

    fun directorySize(file: File = root): Long = runCatching {
        if (file.isFile) file.length() else file.listFiles()?.sumOf(::directorySize) ?: 0L
    }.getOrDefault(0L)

    fun hasLocalServerProfile(): Boolean {
        return File(root, ".minehost/profile.properties").exists()
    }

    fun saveLocalServerProfile(
        settings: ServerSettingsState,
        templateId: String,
        iconPath: String?
    ): OperationResult {
        val dir = File(root, ".minehost").apply { mkdirs() }
        val file = File(dir, "profile.properties")
        val p = Properties()
        if (file.exists()) runCatching { file.inputStream().use(p::load) }
        
        p["created"] = "true"
        p["serverName"] = settings.serverName
        p["templateId"] = templateId
        p["levelName"] = settings.levelName
        p["iconPath"] = iconPath ?: ""
        if (p.getProperty("createdAt").isNullOrBlank()) {
            p["createdAt"] = System.currentTimeMillis().toString()
        }

        return runCatching {
            file.outputStream().use { p.store(it, "MineHost Local Server Profile") }
            OperationResult(true, "Profile saved")
        }.getOrElse { OperationResult(false, it.message ?: "Unable to save profile") }
    }

    fun readLocalServerProfile(): LocalServerProfile? {
        val file = File(root, ".minehost/profile.properties")
        if (!file.exists()) return null
        val p = Properties()
        runCatching { file.inputStream().use(p::load) }.getOrNull() ?: return null

        return LocalServerProfile(
            created = p.getProperty("created", "false").toBoolean(),
            serverName = p.getProperty("serverName", "Local Bedrock Server"),
            templateId = p.getProperty("templateId", ""),
            levelName = p.getProperty("levelName", "world"),
            iconPath = p.getProperty("iconPath").takeIf { it.isNotBlank() },
            createdAt = p.getProperty("createdAt", "0").toLongOrNull() ?: 0L
        )
    }

    fun saveServerIcon(resolver: ContentResolver, uri: Uri): OperationResult {
        val dir = File(root, ".minehost").apply { mkdirs() }
        val target = File(dir, "server-card-icon") // Internal name without extension for simplicity, or we can keep it
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return OperationResult(false, "Unable to open selected icon")
            OperationResult(true, target.absolutePath)
        }.getOrElse { OperationResult(false, it.message ?: "Icon save failed") }
    }

    private fun addToZip(zip: ZipOutputStream, source: File, entryName: String) {
        if (source.isDirectory) {
            val children = source.listFiles()
            if (children.isNullOrEmpty()) {
                zip.putNextEntry(ZipEntry("$entryName/"))
                zip.closeEntry()
            } else children.forEach { addToZip(zip, it, "$entryName/${it.name}") }
        } else {
            zip.putNextEntry(ZipEntry(entryName))
            FileInputStream(source).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else uri.lastPathSegment?.substringAfterLast('/')
        } finally {
            cursor?.close()
        }
    }
}
