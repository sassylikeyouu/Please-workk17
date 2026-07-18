package com.example.tunnel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TunnelStatus {
    STOPPED, STARTING, RUNNING, ERROR
}

/**
 * Placeholder for future tunnel implementations.
 * All zrok-specific logic has been removed.
 */
class TunnelManager(private val context: Context, private val onLog: (String) -> Unit) {
    
    private val _status = MutableStateFlow(TunnelStatus.STOPPED)
    val status: StateFlow<TunnelStatus> = _status

    private val _publicAddress = MutableStateFlow("")
    val publicAddress: StateFlow<String> = _publicAddress

    fun hasToken(): Boolean = false

    fun setToken(token: String) {
        // No-op
    }

    suspend fun startTunnel(port: Int, isUdp: Boolean): Boolean {
        onLog("[Tunnel] Tunnel service is currently disabled.")
        return false
    }

    fun stopTunnel() {
        _status.value = TunnelStatus.STOPPED
        _publicAddress.value = ""
    }
}
