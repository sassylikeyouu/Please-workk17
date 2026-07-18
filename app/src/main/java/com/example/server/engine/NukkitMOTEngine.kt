package com.example.server.engine

import android.content.Context
import com.example.server.ServerStatus

class NukkitMOTEngine(
    context: Context,
    serverDir: java.io.File,
    onLog: (String) -> Unit,
    onStatusChange: (ServerStatus) -> Unit
) : BaseJavaEngine(context, serverDir, onLog, onStatusChange) {
    override val serverJarUrl = "GITHUB_API_NUKKIT_MOT"
    override val serverFolderName = "nukkit-mot"
    override val serverJarName = "Nukkit-MOT.jar"
    override val serverEngineName = "Nukkit-MOT"
    override val minJavaVersion = 25
    override val maxJavaVersion = 25
}
