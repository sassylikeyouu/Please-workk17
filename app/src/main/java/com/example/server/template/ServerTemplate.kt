package com.example.server.template

import com.example.server.ServerType

data class ServerTemplate(
    val id: String,
    val name: String,
    val description: String,
    val serverType: ServerType,
    val defaultMemoryMb: Int,
    val requiredPlugins: List<PluginInfo> = emptyList()
)

data class PluginInfo(
    val name: String,
    val downloadUrl: String
)

object TemplateRegistry {
    val BEDROCK_NUKKIT = ServerTemplate(
        id = "bedrock_nukkit",
        name = "Nukkit",
        description = "Native Bedrock server software",
        serverType = ServerType.BEDROCK_NUKKIT,
        defaultMemoryMb = 600
    )

    val BEDROCK_POWER_NUKKIT = ServerTemplate(
        id = "bedrock_power_nukkit",
        name = "PowerNukkit",
        description = "Native Bedrock server software with advanced features",
        serverType = ServerType.BEDROCK_NUKKIT,
        defaultMemoryMb = 600
    )

    val BEDROCK_POWER_NUKKIT_X = ServerTemplate(
        id = "bedrock_power_nukkit_x",
        name = "PowerNukkitX",
        description = "Advanced Bedrock server software",
        serverType = ServerType.BEDROCK_NUKKIT,
        defaultMemoryMb = 600
    )

    val BEDROCK_CLOUDBURST_NUKKIT = ServerTemplate(
        id = "bedrock_cloudburst_nukkit",
        name = "Cloudburst Nukkit",
        description = "Native Bedrock server software",
        serverType = ServerType.BEDROCK_NUKKIT,
        defaultMemoryMb = 600
    )

    val BEDROCK_NUKKIT_MOT = ServerTemplate(
        id = "nukkit-mot",
        name = "Nukkit-MOT",
        description = "High-performance Java-based Bedrock server software",
        serverType = ServerType.JAVA_NUKKIT_MOT,
        defaultMemoryMb = 600
    )

    val ALL_TEMPLATES = listOf(
        BEDROCK_POWER_NUKKIT_X,
        BEDROCK_POWER_NUKKIT,
        BEDROCK_CLOUDBURST_NUKKIT,
        BEDROCK_NUKKIT_MOT,
        BEDROCK_NUKKIT
    )
}
