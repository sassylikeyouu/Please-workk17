package com.example.data

import java.util.UUID

data class ServerProfile(
    val id: String,
    val name: String,
    val engineId: String,
    val serverDirectory: String,
    val levelName: String,
    val iconPath: String?,
    val port: Int,
    val memoryMb: Int,
    val maxPlayers: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false
) {
    companion object {
        fun createDraft(
            name: String,
            engineId: String,
            serverDirectory: String,
            levelName: String = "world",
            port: Int = 19132,
            memoryMb: Int = 600,
            maxPlayers: Int = 10,
            iconPath: String? = null
        ): ServerProfile {
            val now = System.currentTimeMillis()
            return ServerProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                engineId = engineId,
                serverDirectory = serverDirectory,
                levelName = levelName,
                iconPath = iconPath,
                port = port,
                memoryMb = memoryMb,
                maxPlayers = maxPlayers,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

data class ServerProfileChanges(
    val name: String? = null,
    val engineId: String? = null,
    val levelName: String? = null,
    val iconPath: String? = null,
    val port: Int? = null,
    val memoryMb: Int? = null,
    val maxPlayers: Int? = null,
    val isFavorite: Boolean? = null
)
