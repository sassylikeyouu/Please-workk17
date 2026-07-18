package com.example.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.NetworkInterface

class ServerForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    private var diagnosticJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "server_channel")
            .setContentTitle("Minecraft Server Running")
            .setContentText("The Bedrock server is active in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces) // Placeholder icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(19132, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use no-type or best match for older versions if needed, 
            // but manifest says specialUse which is only 34+.
            // For 29-33, we can pass 0 or a generic type, but usually 0 works if no specific type is required.
            startForeground(19132, notification)
        } else {
            startForeground(19132, notification)
        }

        acquireLocks()
        startDiagnosticsLoop()

        return START_STICKY
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BedrockBox::ServerWakeLock"
            )
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        
        if (wifiLock == null) {
            wifiLock = wifiManager.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "BedrockBox::WifiLock"
            )
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }

        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("BedrockBox::MulticastLock")
        }
        if (multicastLock?.isHeld == false) {
            multicastLock?.acquire()
        }
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
        }
    }

    private fun startDiagnosticsLoop() {
        diagnosticJob?.cancel()
        diagnosticJob = scope.launch {
            while (true) {
                logDiagnostics()
                delay(30000)
            }
        }
    }

    private fun logDiagnostics() {
        Log.i("ServerDiagnostics", "--- 30s Server Diagnostic Report ---")
        Log.i("ServerDiagnostics", "Foreground Service: ACTIVE")
        Log.i("ServerDiagnostics", "WakeLock Held: ${wakeLock?.isHeld == true}")
        Log.i("ServerDiagnostics", "WifiLock Held: ${wifiLock?.isHeld == true}")
        Log.i("ServerDiagnostics", "MulticastLock Held: ${multicastLock?.isHeld == true}")
        
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var hasActiveInterface = false
            if (interfaces != null) {
                for (netInt in interfaces) {
                    if (netInt.isUp && !netInt.isLoopback) {
                        hasActiveInterface = true
                        val addresses = netInt.inetAddresses.toList().joinToString { it.hostAddress ?: "" }
                        Log.i("ServerDiagnostics", "Active Interface: ${netInt.name} - $addresses")
                    }
                }
            }
            if (!hasActiveInterface) {
                Log.w("ServerDiagnostics", "No active external network interfaces found!")
            }
        } catch (e: Exception) {
            Log.e("ServerDiagnostics", "Failed to get network interfaces: ${e.message}")
        }
        
        try {
            // Check if process is holding port 19132
            // We can't directly check the process's ports easily without root, but we can check if it's reachable or assume from java process.
            Log.i("ServerDiagnostics", "Java Process: Assuming running if we reached this point, Check Process stdout for UDP/RakNet")
        } catch (e: Exception) {}
        
        Log.i("ServerDiagnostics", "------------------------------------")
    }

    override fun onDestroy() {
        super.onDestroy()
        diagnosticJob?.cancel()
        releaseLocks()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.w("ServerForegroundService", "Service timed out for type: $fgsType")
        // Note: For specialUse, we might not get timed out as aggressively as shortService,
        // but we should log it.
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "server_channel",
                "Minecraft Server Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
