package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ServerProfileRepository(private val context: Context) {
    private val serversDir = File(context.filesDir, "servers").apply { mkdirs() }
    private val profilesJsonFile = File(serversDir, "profiles.json")

    private val _profiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    suspend fun loadProfiles() = withContext(Dispatchers.IO) {
        if (!profilesJsonFile.exists()) {
            _profiles.value = emptyList()
            return@withContext
        }

        try {
            val json = profilesJsonFile.readText()
            val array = JSONArray(json)
            val list = mutableListOf<ServerProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(parseProfile(obj))
            }
            _profiles.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            _profiles.value = emptyList()
        }
    }

    suspend fun createProfile(draft: ServerCreationDraft): Result<ServerProfile> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val profileDir = File(serversDir, id).apply { mkdirs() }
        val metaDir = File(profileDir, ".minehost").apply { mkdirs() }
        
        val now = System.currentTimeMillis()
        val profile = ServerProfile(
            id = id,
            name = draft.name,
            engineId = draft.engineId,
            serverDirectory = profileDir.absolutePath,
            levelName = draft.levelName,
            iconPath = draft.iconPath, // This might need to be copied into the profile dir later
            port = draft.port,
            memoryMb = draft.memoryMb,
            maxPlayers = draft.maxPlayers,
            createdAt = now,
            updatedAt = now
        )

        val newList = _profiles.value + profile
        saveProfiles(newList).fold(
            onSuccess = {
                saveProfileMetadata(profile)
                _profiles.value = newList
                Result.success(profile)
            },
            onFailure = { 
                profileDir.deleteRecursively()
                Result.failure(it)
            }
        )
    }

    suspend fun updateProfile(serverId: String, changes: ServerProfileChanges): Result<ServerProfile> = withContext(Dispatchers.IO) {
        val currentList = _profiles.value
        val index = currentList.indexOfFirst { it.id == serverId }
        if (index == -1) return@withContext Result.failure(Exception("Profile not found"))

        val current = currentList[index]
        val updated = current.copy(
            name = changes.name ?: current.name,
            engineId = changes.engineId ?: current.engineId,
            levelName = changes.levelName ?: current.levelName,
            iconPath = changes.iconPath ?: current.iconPath,
            port = changes.port ?: current.port,
            memoryMb = changes.memoryMb ?: current.memoryMb,
            maxPlayers = changes.maxPlayers ?: current.maxPlayers,
            isFavorite = changes.isFavorite ?: current.isFavorite,
            updatedAt = System.currentTimeMillis()
        )

        val newList = currentList.toMutableList().apply { set(index, updated) }
        saveProfiles(newList).fold(
            onSuccess = {
                saveProfileMetadata(updated)
                _profiles.value = newList
                Result.success(updated)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun deleteProfile(serverId: String, deleteFiles: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val currentList = _profiles.value
        val profile = currentList.find { it.id == serverId } ?: return@withContext Result.failure(Exception("Profile not found"))

        val newList = currentList.filter { it.id != serverId }
        saveProfiles(newList).fold(
            onSuccess = {
                if (deleteFiles) {
                    File(profile.serverDirectory).deleteRecursively()
                }
                _profiles.value = newList
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getProfile(serverId: String): ServerProfile? = _profiles.value.find { it.id == serverId }

    suspend fun setFavorite(serverId: String, favorite: Boolean): Result<Unit> = 
        updateProfile(serverId, ServerProfileChanges(isFavorite = favorite)).map { Unit }

    private fun saveProfiles(list: List<ServerProfile>): Result<Unit> {
        val array = JSONArray()
        list.forEach { array.put(serializeProfile(it)) }
        
        return try {
            val tempFile = File(serversDir, "profiles.json.tmp")
            tempFile.writeText(array.toString(2))
            if (tempFile.renameTo(profilesJsonFile)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to atomicaly save profiles"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveProfileMetadata(profile: ServerProfile) {
        try {
            val metaFile = File(File(profile.serverDirectory), ".minehost/profile.json")
            metaFile.writeText(serializeProfile(profile).toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseProfile(obj: JSONObject): ServerProfile {
        return ServerProfile(
            id = obj.getString("id"),
            name = obj.getString("name"),
            engineId = obj.getString("engineId"),
            serverDirectory = obj.getString("serverDirectory"),
            levelName = obj.getString("levelName"),
            iconPath = obj.optString("iconPath", null),
            port = obj.getInt("port"),
            memoryMb = obj.getInt("memoryMb"),
            maxPlayers = obj.getInt("maxPlayers"),
            createdAt = obj.getLong("createdAt"),
            updatedAt = obj.getLong("updatedAt"),
            isFavorite = obj.optBoolean("isFavorite", false)
        )
    }

    private fun serializeProfile(profile: ServerProfile): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("engineId", profile.engineId)
            put("serverDirectory", profile.serverDirectory)
            put("levelName", profile.levelName)
            put("iconPath", profile.iconPath)
            put("port", profile.port)
            put("memoryMb", profile.memoryMb)
            put("maxPlayers", profile.maxPlayers)
            put("createdAt", profile.createdAt)
            put("updatedAt", profile.updatedAt)
            put("isFavorite", profile.isFavorite)
        }
    }

    fun getServerProfileRoot(serverId: String): File {
        val root = File(serversDir, serverId).canonicalFile
        if (!root.path.startsWith(serversDir.canonicalPath)) {
            throw SecurityException("Unsafe server ID")
        }
        return root
    }
}
