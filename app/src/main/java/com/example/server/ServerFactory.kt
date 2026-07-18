package com.example.server

import android.content.Context
import com.example.server.engine.CloudburstEngine
import com.example.server.engine.NukkitEngine
import com.example.server.engine.PowerNukkitEngine
import com.example.server.engine.PowerNukkitXEngine
import com.example.server.engine.NukkitMOTEngine
import com.example.server.engine.PaperEngine
import com.example.server.engine.ServerEngine
import com.example.server.template.ServerTemplate
import com.example.server.template.TemplateRegistry

object ServerFactory {
    fun createEngine(
        context: Context,
        serverDir: java.io.File,
        template: ServerTemplate,
        onLog: (String) -> Unit,
        onStatusChange: (ServerStatus) -> Unit
    ): ServerEngine {
        return when (template.id) {
            "bedrock_power_nukkit" -> PowerNukkitEngine(context, serverDir, onLog, onStatusChange)
            "bedrock_power_nukkit_x" -> PowerNukkitXEngine(context, serverDir, onLog, onStatusChange)
            "bedrock_nukkit" -> NukkitEngine(context, serverDir, onLog, onStatusChange)
            "bedrock_cloudburst_nukkit" -> CloudburstEngine(context, serverDir, onLog, onStatusChange)
            "nukkit-mot" -> NukkitMOTEngine(context, serverDir, onLog, onStatusChange)
            "java_paper" -> PaperEngine(context, serverDir, onLog, onStatusChange)
            else -> {
                onLog("Error: Engine ${template.name} is not supported in this version.")
                // Return a dummy engine that just errors out
                object : ServerEngine {
                    override fun startServer(memoryMb: Int) {
                        onLog("Error: ${template.name} engine is not yet fully implemented. Please choose PowerNukkitX, Nukkit, or Cloudburst Nukkit.")
                        onStatusChange(ServerStatus.FAILED)
                    }
                    override fun stopServer() {}
                    override fun restartServer() {}
                    override fun sendCommand(command: String) {}
                    override fun getStatus(): ServerStatus = ServerStatus.STOPPED
                    override fun setOnlineMode(online: Boolean) {}
                    override fun installPlugin(url: String, fileName: String) {}
                    override fun backupWorld() {}
                    override fun getServerDir(): java.io.File = serverDir.apply { mkdirs() }
                }
            }
        }
    }
}
