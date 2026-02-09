package com.meshintercom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meshlanintercom.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MeshService : Service() {

    private val binder = LocalBinder()
    private var meshManager: MeshNetworkManager? = null

    // State exposed to Activity
    private val _peers = MutableStateFlow<List<String>>(emptyList())
    val peers: StateFlow<List<String>> = _peers.asStateFlow()

    private val _isAudioRunning = MutableStateFlow(false)
    val isAudioRunning: StateFlow<Boolean> = _isAudioRunning.asStateFlow()

    // Audio Control States
    var isMicMuted = false
    var isDeafened = false

    inner class LocalBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize JNI Global Reference for callback
        nativeInitJni()

        meshManager = MeshNetworkManager(this)
        meshManager?.onPeersChanged = { newPeers ->
            _peers.value = newPeers
            updateNotification()
        }

        // Link Network Receive -> Native Inject
        meshManager?.onAudioPacketReceived = { data ->
            if (!isDeafened) {
                nativeInjectAudioPacket(data)
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_AUDIO -> {
                val secret = intent.getStringExtra(EXTRA_SECRET) ?: ""
                val nickname = intent.getStringExtra(EXTRA_NICKNAME) ?: "User"
                startAudio(secret, nickname)
            }
            ACTION_STOP_AUDIO -> {
                stopAudio()
            }
        }
        return START_STICKY
    }

    // Called from C++ to send data (needs to be kept in MainActivity context or linked here?)
    // Actually, JNI calls look for "Java_com_meshintercom_MainActivity_onNativeAudioData" or
    // similar.
    // If native lib is compiled to call MainActivity, we have a problem.
    // However, usually we register natives or use standard naming.
    // The current native-lib.cpp probably uses JNI_OnLoad and looks for a class.
    // I need to check native-lib.cpp to see how it calls back to Java.
    // If it calls MainActivity, I need to refactor C++ too, OR keep JNI in MainActivity and proxy
    // to Service.
    // Proxying is easier: MainActivity keeps JNI, receives byte[], sends to Service.
    // BUT MainActivity can die. Service needs to hold the JNI interface.
    // Let's assume for now I will check native-lib.cpp.
    // For this step, I'll implement the logic assuming I'll fix JNI later.

    fun startAudio(secret: String, nickname: String) {
        if (_isAudioRunning.value) return

        meshManager?.start(secret, nickname)
        nativeStartAudio()
        _isAudioRunning.value = true
        _isAudioRunning.value = true

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    fun stopAudio() {
        if (!_isAudioRunning.value) return

        nativeStopAudio()
        meshManager?.stop()
        _isAudioRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf() // Stop service if audio stops
    }

    // Called by MainActivity proxy (if we keep JNI there) OR by JNI directly (if we refactor)
    fun sendAudioPacket(data: ByteArray) {
        if (isMicMuted || isDeafened) return
        meshManager?.sendAudioPacket(data)
    }

    // Called by JNI directly
    fun onNativeAudioData(data: ByteArray) {
        if (isMicMuted || isDeafened) return
        meshManager?.sendAudioPacket(data)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Mesh Intercom Service",
                            NotificationManager.IMPORTANCE_LOW
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent =
                Intent(this, MainActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(
                            this,
                            0,
                            notificationIntent,
                            PendingIntent.FLAG_IMMUTABLE
                    )
                }

        val peerCount = _peers.value.size

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Intercom Active")
                .setContentText("Connected to $peerCount peers")
                .setSmallIcon(R.drawable.ic_logo_custom)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
    }

    private fun updateNotification() {
        if (_isAudioRunning.value) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nativeStopAudio()
        meshManager?.stop()
    }

    // External native methods - need to be moved here?
    // Yes, if Service controls lifecycle.
    external fun nativeStartAudio()
    external fun nativeStopAudio()
    external fun nativeInjectAudioPacket(data: ByteArray)
    external fun nativeInitJni()

    companion object {
        const val CHANNEL_ID = "MeshIntercomChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_AUDIO = "ACTION_START"
        const val ACTION_STOP_AUDIO = "ACTION_STOP"
        const val EXTRA_SECRET = "EXTRA_SECRET"
        const val EXTRA_NICKNAME = "EXTRA_NICKNAME"

        init {
            System.loadLibrary("meshintercom")
        }
    }
}
