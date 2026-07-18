package com.example.server.engine

import com.example.server.ServerStatus

interface ServerEngine {
    fun startServer(memoryMb: Int)
    fun stopServer()
    fun restartServer()
    fun sendCommand(command: String)
    fun getStatus(): ServerStatus
    fun setOnlineMode(online: Boolean)
    fun installPlugin(url: String, fileName: String)
    fun backupWorld()
    fun getServerDir(): java.io.File
    fun getProcessId(): Long? = null
    fun getStartedAtMillis(): Long? = null
}
