package com.example.server.engine

import com.example.server.ServerStatus

import android.content.Context
import com.example.server.Downloader
import com.example.server.JavaRuntimeManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import com.example.server.health.ServerHealthMonitor
import com.example.server.NetworkUtils


abstract class BaseJavaEngine(
    val context: Context,
    private val serverDir: File,
    val onLog: (String) -> Unit,
    val onStatusChange: (ServerStatus) -> Unit
) : ServerEngine {
    abstract val serverJarUrl: String
    abstract val serverFolderName: String
    abstract val serverJarName: String
    abstract val serverEngineName: String
    open val minJavaVersion: Int = 21
    open val maxJavaVersion: Int = 21


    private var process: Process? = null
    @Volatile private var startedAtMillis: Long? = null
    @Volatile private var lastMemoryMb: Int = 600
    private var logJob: Job? = null
    private var onlineMode: Boolean = false // Default: LAN Mode
    private val scope = CoroutineScope(Dispatchers.IO)
    private val healthMonitor = ServerHealthMonitor(onLog, onStatusChange)


    private val rootServerDir: File
        get() = serverDir.apply { mkdirs() }
        
    val serverJar: File
        get() = File(rootServerDir, serverJarName)
        
    val requiredJavaVersion: Int
        get() = maxJavaVersion

    val requiredClassVersion: Double
        get() = if (serverJarName.contains("powernukkit", ignoreCase = true)) 65.0 else 61.0 // PNX requires 65.0

    val jreDir: File
        get() = JavaRuntimeManager.getJreDir(context, requiredJavaVersion)
        
    val javaBin: File
        get() = JavaRuntimeManager.getJavaBin(context, requiredJavaVersion)

    override fun getServerDir(): File = rootServerDir

    fun checkIntegrity() {
        scope.launch {
            val jreValid = JavaRuntimeManager.isJavaReady(context, requiredJavaVersion)
            if (!jreValid || !serverJar.exists()) {
                withContext(Dispatchers.Main) { onLog("Some required files are missing. They will be downloaded automatically when you start the server.") }
            } else {
                withContext(Dispatchers.Main) { onLog("System integrity check passed. Files are ready.") }
            }
        }
    }

    override fun startServer(memoryMb: Int) {
        lastMemoryMb = memoryMb.coerceIn(384, 8192)
        if (process != null) {
            onLog("Server is already running or starting.")
            return
        }

        scope.launch {
            withContext(Dispatchers.Main) { onStatusChange(ServerStatus.PREPARING) }
            
            val jreValid = JavaRuntimeManager.isJavaReady(context, requiredJavaVersion)
            val filesMissing = !jreValid || !serverJar.exists()

            // Start Download Service if files are missing
            if (filesMissing) {
                val downloadIntent = android.content.Intent(context, com.example.server.DownloadForegroundService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(downloadIntent)
                } else {
                    context.startService(downloadIntent)
                }

                withContext(Dispatchers.Main) { 
                    onStatusChange(ServerStatus.DOWNLOADING)
                    onLog("Files missing. Initiating setup...") 
                }
                if (!jreValid) {
                    val success = JavaRuntimeManager.ensureJavaReady(context, requiredJavaVersion) { progress: String ->
                        scope.launch(Dispatchers.Main) { onLog(progress) }
                    }
                    if (!success) {
                        context.stopService(downloadIntent)
                        withContext(Dispatchers.Main) {
                            onLog("ERROR: Java runtime setup failed.")
                            onStatusChange(ServerStatus.FAILED)
                        }
                        return@launch
                    }
                }
                if (!serverJar.exists()) {
                    withContext(Dispatchers.Main) { onLog("2. Download started: $serverJarName") }
                    val downloadSuccess = Downloader.downloadServerJar(serverJarUrl, serverJar) { progress: String ->
                        scope.launch(Dispatchers.Main) { onLog(progress) }
                    }
                    if (!downloadSuccess) {
                        context.stopService(downloadIntent)
                        withContext(Dispatchers.Main) {
                            onLog("ERROR: Server download failed.")
                            onStatusChange(ServerStatus.FAILED)
                        }
                        return@launch
                    }
                }
                // Stop download service after success
                context.stopService(downloadIntent)
                withContext(Dispatchers.Main) { onStatusChange(ServerStatus.PREPARING) }
            }

            // Start Server Foreground Service for actual execution
            val serverIntent = android.content.Intent(context, com.example.server.ServerForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serverIntent)
            } else {
                context.startService(serverIntent)
            }

            // 1. VALIDATION PHASE (DO NOT SKIP)
            withContext(Dispatchers.Main) { 
                onLog("Validating environment...")
            }

            // 1.5. NETWORK AUTHENTICATION DIAGNOSTICS
            var dnsPass = false
            var jwksPass = false
            var abortStartup = false
            
            if (onlineMode) {
                withContext(Dispatchers.Main) { onLog("Testing Xbox authentication network...") }
                try {
                    val authUrl = java.net.URL("https://authorization.franchise.minecraft-services.net/.well-known/keys")
                    val authConn = authUrl.openConnection() as java.net.HttpURLConnection
                    authConn.connectTimeout = 10000
                    authConn.readTimeout = 10000
                    authConn.requestMethod = "GET"
                    
                    try {
                        val address = java.net.InetAddress.getByName("authorization.franchise.minecraft-services.net")
                        dnsPass = address.hostAddress != null
                    } catch (e: Exception) {
                        try {
                            val dohUrl = java.net.URL("https://dns.google/resolve?name=authorization.franchise.minecraft-services.net&type=A")
                            val conn = dohUrl.openConnection() as java.net.HttpURLConnection
                            if (conn.responseCode == 200) {
                                val json = conn.inputStream.bufferedReader().use { it.readText() }
                                dnsPass = json.contains("\"data\"")
                            }
                        } catch (e2: Exception) {}
                    }

                    try {
                        authConn.connect()
                        val code = authConn.responseCode
                        if (code == 200) {
                            val response = authConn.inputStream.bufferedReader().use { it.readText() }
                            if (response.contains("keys") || response.contains("kid")) {
                                jwksPass = true
                                val jwksFile = java.io.File(rootServerDir, "jwks_cache.json")
                                jwksFile.writeText(response)
                            }
                        } else {
                            withContext(Dispatchers.Main) { onLog("Xbox Auth Server returned code: $code") }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { onLog("Direct connection failed: ${e.message}") }
                        val jwksFile = java.io.File(rootServerDir, "jwks_cache.json")
                        if (jwksFile.exists() && jwksFile.length() > 0) {
                            if (System.currentTimeMillis() - jwksFile.lastModified() < 604800000) { // 7 days
                                withContext(Dispatchers.Main) { onLog("Using cached JWKS keys (offline).") }
                                jwksPass = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onLog("Network diagnostics encountered an error: ${e.message}") }
                }
                
                withContext(Dispatchers.Main) {
                    onLog("Xbox Auth Diagnostics:")
                    onLog("DNS: " + if (dnsPass) "PASS" else "FAIL")
                    onLog("JWKS Download: " + if (jwksPass) "PASS" else "FAIL")
                    
                    val now = System.currentTimeMillis()
                    onLog("Device Time: ${java.util.Date(now)}")
                    if (now < 1704067200000L) { // Before Jan 1 2024
                        onLog("WARNING: Device time appears to be incorrect. Xbox Auth WILL fail.")
                    }
                    
                    if (dnsPass && jwksPass) {
                        onLog("Xbox authentication state: AVAILABLE")
                    } else {
                        onLog("!!! Xbox authentication server unreachable !!!")
                        onLog("Players will get \"Invalid login data\".")
                        onLog("ADVICE: Switch to LAN Mode or check Internet/Time.")
                        
                        if (!jwksPass) {
                            onLog("ABORTING: Online mode requested but authentication server is unreachable.")
                            onStatusChange(com.example.server.ServerStatus.FAILED)
                            abortStartup = true
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) { onLog("LAN Mode active: Skipping Xbox Auth checks.") }
            }

            if (abortStartup) return@launch

            withContext(Dispatchers.Main) {
                onLog("1. Selected engine: $serverEngineName")
                onLog("Download URL: $serverJarUrl")
                onLog("Downloaded filename: $serverJarName")
                onLog("File location: ${serverJar.absolutePath}")
                if (serverJar.exists()) {
                    val sizeMb = serverJar.length() / (1024 * 1024)
                    onLog("File size: $sizeMb MB")
                } else {
                    onLog("File size: 0 MB (Not found)")
                }
                
                // Also print the exact example format from the prompt to be safe
                onLog("Selected Engine: $serverEngineName")
                onLog("Jar:\n$serverJarName")
                onLog("Path:\n${serverJar.absolutePath}")
                if (serverJar.exists()) {
                    val sizeMb = serverJar.length() / (1024 * 1024)
                    onLog("Size:\n$sizeMb MB")
                } else {
                    onLog("Size:\n0 MB")
                }
            }
            if (!javaBin.exists()) {
                withContext(Dispatchers.Main) {
                    onLog("Error: Java runtime missing")
                    onLog("File not found: ${javaBin.absolutePath}")
                    onStatusChange(ServerStatus.FAILED)
                }
                return@launch
            }
            if (!serverJar.exists()) {
                withContext(Dispatchers.Main) {
                    onLog("Error: Server file missing")
                    onLog("File not found: ${serverJar.absolutePath}")
                    onStatusChange(ServerStatus.FAILED)
                }
                return@launch
            }
            
            withContext(Dispatchers.Main) { onLog("Validating jar file...") }
            var isValidJar = false
            try {
                if (serverJar.length() > 1_000_000) { // at least 1MB
                    java.util.zip.ZipFile(serverJar).use { zip ->
                        val manifest = zip.getEntry("META-INF/MANIFEST.MF")
                        var hasClass = false
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.name.endsWith(".class")) {
                                hasClass = true
                                break
                            }
                        }
                        
                        if (manifest != null && hasClass) {
                            val manifestContent = zip.getInputStream(manifest).bufferedReader().readText()
                            if (manifestContent.contains("Main-Class:")) {
                                isValidJar = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                isValidJar = false
            }
            
            if (!isValidJar) {
                serverJar.delete()
                withContext(Dispatchers.Main) {
                    onLog("Error: Invalid server jar downloaded.")
                    onLog("Deleted corrupted file. Please try again.")
                    onStatusChange(ServerStatus.FAILED)
                }
                return@launch
            }

            withContext(Dispatchers.Main) { 
                onLog("4. Validation passed")
                onLog("Preparing discovery cache...")
            }
            
            val diagPassed = com.example.server.NetworkDiagnosticsManager.runDiagnostics(context, rootServerDir, onLog)
            if (!diagPassed) {
                withContext(Dispatchers.Main) {
                    onLog("Diagnostics failed! Aborting server start.")
                    onStatusChange(com.example.server.ServerStatus.FAILED)
                }
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                launchServerProcess(memoryMb)
            }
        }
    }

    private fun launchServerProcess(memoryMb: Int) {
        logJob = scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onStatusChange(ServerStatus.STARTING) }
            try {
                // Step 1: Initialize environment
                File(rootServerDir, "world").mkdirs()
                File(rootServerDir, "logs").mkdirs()
                File(rootServerDir, "eula.txt").writeText("eula=true\n")
                
                val authVal = if (onlineMode) "on" else "off"
                val authBool = if (onlineMode) "true" else "false"
                val serverProps = File(rootServerDir, "server.properties")
                val configuredPort = runCatching {
                    if (!serverProps.exists()) return@runCatching 19132
                    java.util.Properties().apply {
                        serverProps.inputStream().use(::load)
                    }.getProperty("server-port", "19132").toIntOrNull()?.coerceIn(1024, 65535) ?: 19132
                }.getOrDefault(19132)

                val nukkitYml = File(rootServerDir, "nukkit.yml")
                val pnxYml = File(rootServerDir, "powernukkitx.yml")
                val pnxConfig = File(rootServerDir, "config.yml")
                
                fun patchYml(file: File) {
                    if (!file.exists()) {
                        file.writeText(
                            "settings:\n" +
                            "  language: \"eng\"\n" +
                            "  force-language: true\n" +
                            "  xbox-auth: $authBool\n" +
                            "  debug: 2\n" +
                            "network:\n" +
                            "  ip: \"0.0.0.0\"\n" +
                            "  port: $configuredPort\n"
                        )
                    } else {
                        var content = file.readText()
                        if (content.contains("xbox-auth:")) {
                            content = content.replace(Regex("xbox-auth:.*"), "xbox-auth: $authBool")
                        } else {
                            if (content.contains("settings:")) {
                                content = content.replace("settings:", "settings:\n  xbox-auth: $authBool")
                            } else {
                                content = "settings:\n  xbox-auth: $authBool\n" + content
                            }
                        }
                        if (Regex("""(?m)^\s*port\s*:""").containsMatchIn(content)) {
                            content = content.replace(
                                Regex("""(?m)^(\s*port\s*:)\s*.*$"""),
                                "$1 $configuredPort"
                            )
                        } else if (content.contains("network:")) {
                            content = content.replace("network:", "network:\n  port: $configuredPort")
                        }
                        file.writeText(content)
                    }
                }

                patchYml(nukkitYml)
                patchYml(pnxYml)
                patchYml(pnxConfig)
                
                if (!serverProps.exists()) {
                    serverProps.writeText(
                        "server-ip=0.0.0.0\n" +
                        "server-port=$configuredPort\n" +
                        "xbox-auth=$authVal\n" +
                        "view-distance=8\n" +
                        "max-players=10\n" +
                        "allow-flight=true\n" +
                        "spawn-protection=0\n" +
                        "white-list=off\n"
                    )
                } else {
                    var propsContent = serverProps.readText()
                    var changed = false
                    if (!propsContent.contains("xbox-auth=$authVal")) {
                        propsContent = propsContent.replace(Regex("xbox-auth=.*"), "xbox-auth=$authVal")
                        if (!propsContent.contains("xbox-auth=$authVal")) propsContent += "xbox-auth=$authVal\n"
                        changed = true
                    }
                    if (!propsContent.contains("server-ip=0.0.0.0")) {
                        propsContent = propsContent.replace(Regex("server-ip=.*"), "server-ip=0.0.0.0")
                        if (!propsContent.contains("server-ip=0.0.0.0")) propsContent += "server-ip=0.0.0.0\n"
                        changed = true
                    }
                    if (changed) {
                        serverProps.writeText(propsContent)
                    }
                }
                
                withContext(Dispatchers.Main) { 
                    onLog("Server configuration verified (0.0.0.0:$configuredPort, Xbox Auth: ${authVal.uppercase()})")
                    onLog("Initializing Java process...") 
                }

                
                
                withContext(Dispatchers.Main) {
                    healthMonitor.onProcessStart()
                }
                
                rootServerDir.mkdirs()
                val tmpDir = File(context.cacheDir, "pnx_tmp").apply { mkdirs() }
                
                Log.i("ServerManager", "Starting server with JRE at: ${jreDir.absolutePath}")
                Log.i("ServerManager", "Server working dir: ${rootServerDir.absolutePath}")
                Log.i("ServerManager", "Java home property: ${jreDir.absolutePath}")
                
                withContext(Dispatchers.Main) {
                    onLog("5. Correct jar launched: $serverJarName")
                    onLog("Working directory: ${rootServerDir.absolutePath}")
                    onLog("Temp directory: ${tmpDir.absolutePath}")
                    onLog("JAVA_HOME: ${jreDir.absolutePath}")
                    onLog("HOME: ${rootServerDir.absolutePath}")
                    onLog("Jar path: ${serverJar.absolutePath}")
                    onLog("Working directory exists: ${rootServerDir.exists()}")
                    onLog("Temp directory exists: ${tmpDir.exists()}, isAbsolute: ${tmpDir.isAbsolute}")
                    
                    if (!rootServerDir.exists()) {
                        onLog("ERROR: Working directory does not exist: ${rootServerDir.absolutePath}")
                        onStatusChange(ServerStatus.FAILED)
                    }
                    if (!tmpDir.exists()) {
                        onLog("ERROR: Temp directory does not exist: ${tmpDir.absolutePath}")
                        onStatusChange(ServerStatus.FAILED)
                    }
                }
                
                if (!rootServerDir.exists() || !tmpDir.exists()) {
                    return@launch
                }
                

                val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalRamMb = (memoryInfo.totalMem / (1024 * 1024)).toInt()
                val availRamMb = (memoryInfo.availMem / (1024 * 1024)).toInt()
                val arch = android.os.Build.SUPPORTED_ABIS.joinToString(", ")
                
                withContext(Dispatchers.Main) {
                    onLog("Device Architecture: $arch")
                    onLog("Device RAM: Total = ${totalRamMb}MB, Available = ${availRamMb}MB")
                }
                
                val argsList = mutableListOf<String>()
                argsList.add(javaBin.absolutePath)
                
                
                
                argsList.add("-Djava.io.tmpdir=${tmpDir.absolutePath}")
                argsList.add("-Duser.dir=${rootServerDir.absolutePath}")
                argsList.add("-Djava.net.preferIPv4Stack=true")
                argsList.add("-Djava.net.preferIPv4Addresses=true")
                argsList.add("-Dhttps.protocols=TLSv1.2,TLSv1.3")
                argsList.add("-Dsun.net.client.defaultConnectTimeout=10000")
                argsList.add("-Dsun.net.client.defaultReadTimeout=10000")
                argsList.add("-Dfile.encoding=UTF-8")
                
                argsList.add("-Dorg.jline.terminal.dumb=true")
                argsList.add("-Djline.terminal=jline.UnsupportedTerminal")
                
                val safeMemoryMb = lastMemoryMb.coerceAtLeast(384)
                val initialMemoryMb = (safeMemoryMb / 4).coerceAtLeast(128)
                argsList.add("-Xms${initialMemoryMb}M")
                argsList.add("-Xmx${safeMemoryMb}M")
                argsList.add("-XX:+UseSerialGC")
                argsList.add("-Djava.awt.headless=true")
                
                // DNS resolution moved to NetworkDiagnosticsManager
                
                val cacertsFile = java.io.File(jreDir, "lib/security/cacerts")
                val securityDir = java.io.File(jreDir, "lib/security")
                if (!securityDir.exists()) securityDir.mkdirs()
                
                if (!cacertsFile.exists() || cacertsFile.length() < 1000) {
                    try {
                        val keyStore = java.security.KeyStore.getInstance("AndroidCAStore")
                        keyStore.load(null, null)
                        
                        val p12 = java.security.KeyStore.getInstance("PKCS12")
                        p12.load(null, null)
                        
                        val aliases = keyStore.aliases()
                        while (aliases.hasMoreElements()) {
                            val alias = aliases.nextElement()
                            val cert = keyStore.getCertificate(alias)
                            p12.setCertificateEntry(alias, cert)
                        }
                        
                        java.io.FileOutputStream(cacertsFile).use { fos ->
                            p12.store(fos, "changeit".toCharArray())
                        }
                        withContext(Dispatchers.Main) { onLog("Installed Android system CA certificates into JRE cacerts.") }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { onLog("Failed to create cacerts: ${e.message}") }
                    }
                }
                
                argsList.add("-jar")
                argsList.add(serverJar.absolutePath)
                argsList.add("nogui")
                
                val args = argsList.toTypedArray()

                
                
                
                // DIAGNOSTIC
                var diagFailed = false
                withContext(Dispatchers.Main) {
                    onLog("--- Server Startup Diagnostics ---")
                    onLog("Detected class version requirement: $requiredClassVersion")
                    onLog("Selected Java version: $requiredJavaVersion")
                    onLog("JAVA_HOME: ${jreDir.absolutePath}")
                    onLog("Java executable path: ${javaBin.absolutePath}")
                    onLog("Java architecture: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}")
                    onLog("Server jar path: ${serverJar.absolutePath}")
                    onLog("Server jar size: ${serverJar.length()} bytes")
                    onLog("Working directory: ${rootServerDir.absolutePath}")
                    onLog("Temporary directory: ${tmpDir.absolutePath}")
                    onLog("LD_LIBRARY_PATH: ${jreDir.absolutePath}/lib:${jreDir.absolutePath}/lib/server:${jreDir.absolutePath}/lib/jli:/system/lib64")
                    
                    if (!javaBin.exists()) {
                        onLog("ERROR: Java executable not found")
                        diagFailed = true
                    } else if (!javaBin.canExecute()) {
                        onLog("ERROR: Java executable is not executable")
                        diagFailed = true
                    }
                    
                    if (!serverJar.exists()) {
                        onLog("ERROR: Server JAR not found")
                        diagFailed = true
                    } else if (serverJar.length() == 0L) {
                        onLog("ERROR: Server JAR is empty")
                        diagFailed = true
                    }
                }
                
                if (diagFailed) {
                    withContext(Dispatchers.Main) { onStatusChange(com.example.server.ServerStatus.FAILED) }
                    return@launch
                }
                
                val envMap = mutableMapOf<String, String>()
                envMap["JAVA_HOME"] = jreDir.absolutePath
                envMap["HOME"] = rootServerDir.absolutePath
                envMap["TMPDIR"] = tmpDir.absolutePath
                envMap["DNS_SERVER"] = "automatic"
                val ldLibPath = JavaRuntimeManager.getLdLibraryPath(context, requiredJavaVersion)
                envMap["LD_LIBRARY_PATH"] = ldLibPath
                
                try {
                    val diagPbX = ProcessBuilder(javaBin.absolutePath, "-Xint", "-version")
                    diagPbX.directory(rootServerDir)
                    diagPbX.environment().putAll(envMap)
                    diagPbX.environment().remove("JAVA_TOOL_OPTIONS")
                    diagPbX.environment().remove("_JAVA_OPTIONS")
                    val pDiagX = diagPbX.start()
                    val readerDiagX = java.io.BufferedReader(java.io.InputStreamReader(pDiagX.inputStream))
                    val errorDiagX = java.io.BufferedReader(java.io.InputStreamReader(pDiagX.errorStream))
                    var lineDiagX: String?
                    while (readerDiagX.readLine().also { lineDiagX = it } != null) {
                        withContext(Dispatchers.Main) { onLog("java settings stdout: $lineDiagX") }
                    }
                    while (errorDiagX.readLine().also { lineDiagX = it } != null) {
                        withContext(Dispatchers.Main) { onLog("java settings stderr: $lineDiagX") }
                    }
                    pDiagX.waitFor()

                    val diagPb = ProcessBuilder(javaBin.absolutePath, "-version")
                    diagPb.directory(rootServerDir)
                    diagPb.environment().putAll(envMap)
                    diagPb.environment().remove("JAVA_TOOL_OPTIONS")
                    diagPb.environment().remove("_JAVA_OPTIONS")
                    val pDiag = diagPb.start()
                    val readerDiag = java.io.BufferedReader(java.io.InputStreamReader(pDiag.inputStream))
                    val errorDiag = java.io.BufferedReader(java.io.InputStreamReader(pDiag.errorStream))
                    
                    var lineDiag: String?
                    var versionOutput = ""
                    while (readerDiag.readLine().also { lineDiag = it } != null) {
                        versionOutput += lineDiag + "\n"
                        withContext(Dispatchers.Main) { onLog("java -version stdout: $lineDiag") }
                    }
                    while (errorDiag.readLine().also { lineDiag = it } != null) {
                        versionOutput += lineDiag + "\n"
                        withContext(Dispatchers.Main) { onLog("java -version stderr: $lineDiag") }
                    }
                    val diagExit = pDiag.waitFor()
                    withContext(Dispatchers.Main) { 
                        onLog("java -version exit code: $diagExit")
                        if (diagExit != 0) {
                            onLog("ERROR: Java exit code is not 0. Aborting.")
                            onStatusChange(com.example.server.ServerStatus.FAILED)
                            diagFailed = true
                        }
                        if (!versionOutput.contains("\"$requiredJavaVersion")) {
                            onLog("ERROR: Java version is not $requiredJavaVersion. Aborting.")
                            onStatusChange(com.example.server.ServerStatus.FAILED)
                            diagFailed = true
                        }
                    }

                    // Module Check
                    if (!diagFailed) {
                        val listModulesPb = ProcessBuilder(javaBin.absolutePath, "--list-modules")
                        listModulesPb.directory(rootServerDir)
                        listModulesPb.environment().putAll(envMap)
                        val pList = listModulesPb.start()
                        val modulesOutput = pList.inputStream.bufferedReader().readText()
                        pList.waitFor()
                        
                        withContext(Dispatchers.Main) {
                            onLog("--- Java Modules ---")
                            modulesOutput.lines().take(10).forEach { onLog(it) }
                            if (modulesOutput.lines().size > 10) onLog("... and more")
                        }
                        
                        val requiredModules = listOf(
                            "java.base", "java.logging", "java.management",
                            "jdk.compiler", "jdk.crypto.ec", "jdk.crypto.cryptoki"
                        )
                        
                        withContext(Dispatchers.Main) {
                            onLog("Checking required modules...")
                            var missingModule = false
                            requiredModules.forEach { module ->
                                if (modulesOutput.contains(module)) {
                                    onLog("Module $module: FOUND")
                                } else {
                                    onLog("Module $module: MISSING")
                                    missingModule = true
                                }
                            }
                            if (missingModule) {
                                onLog("ERROR: Required Java modules missing.")
                                diagFailed = true
                            }
                        }
                    }

                    // Test program
                    if (!diagFailed) {
                        val testFile = java.io.File(rootServerDir, "HelloWorld.java")
                        testFile.writeText("public class HelloWorld { public static void main(String[] args) { System.out.println(\"HelloWorld Test Program Success!\"); } }")
                        
                        val testPb = ProcessBuilder(javaBin.absolutePath, testFile.absolutePath)
                        testPb.directory(rootServerDir)
                        testPb.environment().putAll(envMap)
                        testPb.environment().remove("JAVA_TOOL_OPTIONS")
                        testPb.environment().remove("_JAVA_OPTIONS")
                        val pTest = testPb.start()
                        val readerTest = java.io.BufferedReader(java.io.InputStreamReader(pTest.inputStream))
                        val errorTest = java.io.BufferedReader(java.io.InputStreamReader(pTest.errorStream))
                        var lineTest: String?
                        while (readerTest.readLine().also { lineTest = it } != null) {
                            withContext(Dispatchers.Main) { onLog("Test program stdout: $lineTest") }
                        }
                        while (errorTest.readLine().also { lineTest = it } != null) {
                            withContext(Dispatchers.Main) { onLog("Test program stderr: $lineTest") }
                        }
                        val testExit = pTest.waitFor()
                        withContext(Dispatchers.Main) {
                            onLog("Test program exit code: $testExit")
                            if (testExit != 0) {
                                onLog("ERROR: Java test program failed.")
                                diagFailed = true
                            }
                        }
                    }
                } catch(e: Exception) {
                    withContext(Dispatchers.Main) { 
                        onLog("ERROR: java -version failed: ${e.message}")
                        onStatusChange(com.example.server.ServerStatus.FAILED)
                    }
                    diagFailed = true
                }
                
                if (diagFailed) {
                    withContext(Dispatchers.IO) {
                        jreDir.deleteRecursively()
                    }
                    withContext(Dispatchers.Main) { 
                        onLog("Aborting server launch due to diagnostic failure. JRE/JDK directory has been deleted to trigger a fresh download upon next launch.") 
                    }
                    return@launch
                }
                                
                val pb = ProcessBuilder(args.toList())
                pb.directory(rootServerDir)
                // // pb.environment().clear()
                pb.environment().putAll(envMap)
                pb.environment().remove("JAVA_TOOL_OPTIONS")
                pb.environment().remove("_JAVA_OPTIONS")
                pb.environment().remove("http_proxy")
                pb.environment().remove("https_proxy")
                pb.environment().remove("HTTP_PROXY")
                pb.environment().remove("HTTPS_PROXY")
                pb.redirectErrorStream(false)
                
                withContext(Dispatchers.Main) {
                    onLog("Executable:\n${args[0]}")
                    onLog("Arguments:\n${args.drop(1).joinToString(" ")}")
                    onLog("Environment:\nJAVA_HOME=${envMap["JAVA_HOME"]}, HOME=${envMap["HOME"]}, TMPDIR=${envMap["TMPDIR"]}, LD_LIBRARY_PATH=${envMap["LD_LIBRARY_PATH"]}")
                    onLog("Working directory:\n${rootServerDir.absolutePath}")
                }
                
                process = pb.start()
                startedAtMillis = System.currentTimeMillis()
                
                
                val stdoutBuffer = java.util.concurrent.ConcurrentLinkedQueue<String>()
                val stderrBuffer = java.util.concurrent.ConcurrentLinkedQueue<String>()

                val pReader = java.io.BufferedReader(java.io.InputStreamReader(process!!.inputStream, Charsets.UTF_8))
                val pErrorReader = java.io.BufferedReader(java.io.InputStreamReader(process!!.errorStream, Charsets.UTF_8))
                
                val logJob2 = scope.launch(Dispatchers.IO) {
                    try {
                        pReader.forEachLine { line ->
                            stdoutBuffer.offer(line)
                            if (stdoutBuffer.size > 200) stdoutBuffer.poll()
                            scope.launch(Dispatchers.Main) { 
                                onLog(line)
                                healthMonitor.analyzeLogLine(line)
                            }
                            if (line.contains("Listening on ")) {
                                Log.i("ServerManager", "[DEBUG] Current server bind address and Port: $line")
                                try {
                                    val localIp = NetworkUtils.getLocalIpAddress()
                                    scope.launch(Dispatchers.Main) {
                                        onLog("--- HOW TO CONNECT ---")
                                        onLog("Local IP: $localIp")
                                        onLog("Port: $configuredPort")
                                        onLog("Use this IP on your other device in the same WiFi.")
                                        onLog("----------------------")
                                    }
                                } catch (e: Exception) {}
                            }
                            if (line.contains("UDP socket") || line.contains("RakNet")) {
                                Log.i("ServerManager", "[DEBUG] UDP socket opened successfully: $line")
                            }
                            
                            // Detailed Player Join Diagnostics
                            if (line.contains("LoginPacket", ignoreCase = true)) {
                                val clientIp = if (line.contains("/")) line.substringAfter("/").substringBefore(":") else "Unknown"
                                scope.launch(Dispatchers.Main) { onLog("[DIAG] Incoming Login from $clientIp") }
                                if (line.contains("protocol", ignoreCase = true)) {
                                    val version = line.substringAfter("protocol", "").substringBefore(",", "").trim()
                                    if (version.isNotEmpty()) {
                                        scope.launch(Dispatchers.Main) { onLog("[DIAG] Client Protocol: $version") }
                                    }
                                }
                            }
                            
                            if (line.contains("ServerNetworkSession", ignoreCase = true)) {
                                if (line.contains("fail", ignoreCase = true) || line.contains("error", ignoreCase = true)) {
                                    scope.launch(Dispatchers.Main) { 
                                        onLog("[DIAG] Network session issue: $line")
                                    }
                                }
                            }
                            
                            if (line.contains("Encryption enabled", ignoreCase = true)) {
                                scope.launch(Dispatchers.Main) { onLog("[DIAG] Encryption handshake successful.") }
                            }

                            if (line.contains("Xbox Live", ignoreCase = true) || line.contains("authenticated", ignoreCase = true)) {
                                if (line.contains("failed", ignoreCase = true)) {
                                    scope.launch(Dispatchers.Main) { onLog("[DIAG] Xbox Live authentication FAILED.") }
                                } else if (line.contains("successful", ignoreCase = true) || line.contains("passed", ignoreCase = true)) {
                                    scope.launch(Dispatchers.Main) { onLog("[DIAG] Xbox Live authentication SUCCESS.") }
                                }
                            }
                            
                            if (line.contains("disconnection reason:", ignoreCase = true)) {
                                val reason = line.substringAfter("disconnection reason:").trim()
                                scope.launch(Dispatchers.Main) { onLog("[DIAG] Disconnect Reason: $reason") }
                            }
                            
                            if (line.contains("Exception", ignoreCase = true) && (line.contains("protocol") || line.contains("packet") || line.contains("network"))) {
                                scope.launch(Dispatchers.Main) { onLog("[DIAG] Network Exception: $line") }
                            }

                            if (line.contains("UnknownHostException: authorization.franchise.minecraft-services.net") || 
                                line.contains("InvalidJwtException") || 
                                line.contains("Unable to find suitable verification key") ||
                                line.contains("Invalid login data")) {
                                scope.launch(Dispatchers.Main) {
                                    onLog("!!! AUTHENTICATION ERROR !!!")
                                    onLog("Xbox authentication server unreachable or returned invalid data.")
                                    onLog("Check internet connection or switch to LAN mode in settings.")
                                }
                            }
                            if (line.contains("logged in with entity id") || line.contains("Player connected") || line.contains("Player authenticated:")) {
                                scope.launch(Dispatchers.Main) { onLog("Xbox authentication state: VERIFIED") }
                            }
                            if (line.contains("Enter a language code from the list below")) {
                                Log.i("ServerManager", "[JRE] First run detected")
                                scope.launch(Dispatchers.Main) { onLog("[JRE] Applying default language: eng") }
                                process?.outputStream?.write("eng\n".toByteArray())
                                process?.outputStream?.flush()
                            }
                            if (line.contains("You MUST accept this license to continue") || line.contains("Do you accept the license")) {
                                Log.i("ServerManager", "[JRE] Auto-accepting license")
                                scope.launch(Dispatchers.Main) { onLog("[JRE] Auto-accepting license") }
                                process?.outputStream?.write("yes\n".toByteArray())
                                process?.outputStream?.flush()
                            }
                        }
                    } catch (e: Exception) {}
                }

                val logJob3 = scope.launch(Dispatchers.IO) {
                    try {
                        pErrorReader.forEachLine { line ->
                            stderrBuffer.offer(line)
                            if (stderrBuffer.size > 200) stderrBuffer.poll()
                            scope.launch(Dispatchers.Main) { 
                                onLog("[STDERR] $line")
                            }
                        }
                    } catch (e: Exception) {}
                }
                
                val exitCode = process!!.waitFor()
                logJob2.cancel()
                logJob3.cancel()
                
                withContext(Dispatchers.Main) {
                    onLog("Server process exited with code $exitCode")
                    
                    if (exitCode != 0) {
                        onLog("--- CRASH DIAGNOSTICS ---")
                        val signal = if (exitCode > 128) exitCode - 128 else null
                        onLog("Exit code: $exitCode")
                        if (signal != null) {
                            onLog("Signal causing termination: $signal")
                            if (signal == 6) onLog("Signal details: SIGABRT")
                            if (signal == 9) onLog("Signal details: SIGKILL")
                            if (signal == 11) onLog("Signal details: SIGSEGV")
                        }
                        onLog("--- STDOUT (last 50 lines) ---")
                        stdoutBuffer.toList().takeLast(50).forEach { onLog(it) }
                        onLog("--- STDERR (last 50 lines) ---")
                        stderrBuffer.toList().takeLast(50).forEach { onLog(it) }
                        onLog("-------------------------")
                    }
                    
                    val crashLogs = rootServerDir.listFiles { _, name -> name.startsWith("hs_err_pid") && name.endsWith(".log") }
                    crashLogs?.forEach { crashLog ->
                        onLog("--- JVM CRASH LOG FOUND: ${crashLog.name} ---")
                        try {
                            crashLog.useLines { lines ->
                                lines.take(100).forEach { onLog(it) }
                            }
                        } catch (e: Exception) {
                            onLog("Failed to read crash log: ${e.message}")
                        }
                    }
                    
                    healthMonitor.onProcessExit(exitCode)
                    process = null
                }
} catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    e.printStackTrace()
                    onLog("Startup failure: ${e.message}")
                    onLog("Java path: ${javaBin.absolutePath}")
                    onLog("Server jar: ${serverJar.absolutePath}")
                    onLog("Working dir: ${rootServerDir.absolutePath}")
                    onLog("Temp dir: ${File(context.cacheDir, "pnx_tmp").absolutePath}")
                    onLog(e.stackTraceToString())
                    healthMonitor.setStatus(ServerStatus.FAILED)
                    process = null
                }
            }
        }
    }

    override fun stopServer() {
        val serviceIntent = android.content.Intent(context, com.example.server.ServerForegroundService::class.java)
        serviceIntent.action = "STOP"
        context.startService(serviceIntent)
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { 
                healthMonitor.setStatus(ServerStatus.STOPPING)
                onLog("Stopping server process...") 
            }
            
            val p = process
            if (p != null) {
                try {
                    p.outputStream?.write("stop\n".toByteArray())
                    p.outputStream?.flush()
                } catch (e: Exception) {
                    // Ignore exception
                }

                var waited = 0
                while (waited < 100) { // Wait up to 10 seconds for graceful shutdown
                    try {
                        p.exitValue()
                        break // Process ended
                    } catch (e: IllegalThreadStateException) {
                        // Still running
                    }
                    kotlinx.coroutines.delay(100)
                    waited++
                }

                try { p.inputStream?.close() } catch (e: Exception) {}
                try { p.outputStream?.close() } catch (e: Exception) {}
                try { p.errorStream?.close() } catch (e: Exception) {}

                try {
                    p.exitValue()
                } catch (e: IllegalThreadStateException) {
                    // Process still alive
                    p.destroy()
                    kotlinx.coroutines.delay(500)
                    try {
                        p.exitValue()
                    } catch (e2: IllegalThreadStateException) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            p.destroyForcibly()
                        }
                    }
                }
            }

            process = null
            startedAtMillis = null
            logJob?.cancel()
            logJob = null
            
            withContext(Dispatchers.Main) { 
                healthMonitor.setStatus(ServerStatus.STOPPED)
                onLog("Server stopped.")
            }
        }
    }

    override fun restartServer() {
        if (process == null) {
            onLog("Server is not running.")
            return
        }
        onLog("Restarting server...")
        stopServer()
        scope.launch(Dispatchers.IO) {
            repeat(150) {
                if (process == null) {
                    withContext(Dispatchers.Main) {
                        startServer(lastMemoryMb)
                    }
                    return@launch
                }
                kotlinx.coroutines.delay(100)
            }
            withContext(Dispatchers.Main) {
                onLog("Restart timed out while waiting for the old process to stop.")
                onStatusChange(ServerStatus.FAILED)
            }
        }
    }
    override fun sendCommand(command: String) {
        try {
            process?.outputStream?.let { stream ->
                stream.write("$command\n".toByteArray())
                stream.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getProcessId(): Long? {
        val current = process ?: return null
        // 1. Try Java 9+ Process.pid() if available
        try {
            val method = current.javaClass.getMethod("pid")
            return (method.invoke(current) as Number).toLong()
        } catch (e: Exception) {
            // Fallback
        }

        // 2. Try reflection on the 'pid' field (common in older Android/UNIXProcess)
        try {
            val field = current.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            return (field.get(current) as Number).toLong()
        } catch (e: Exception) {
            // Fallback
        }

        // 3. Try parsing toString() - many Process implementations include it
        try {
            val str = current.toString()
            val match = Regex("pid=(\\d+)").find(str)
            if (match != null) return match.groupValues[1].toLong()
        } catch (e: Exception) {
            // Fallback
        }

        return null
    }

    override fun getStartedAtMillis(): Long? = startedAtMillis

    override fun getStatus(): com.example.server.ServerStatus {
        return healthMonitor.getStatus()
    }

    override fun setOnlineMode(online: Boolean) {
        this.onlineMode = online
    }

    override fun installPlugin(url: String, fileName: String) {
        onLog("Plugin installation not yet fully implemented for Nukkit.")
    }

    override fun backupWorld() {
        onLog("World backup not yet implemented for Nukkit.")
    }
}
