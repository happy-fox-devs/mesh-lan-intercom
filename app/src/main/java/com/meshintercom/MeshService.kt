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
        set(value) {
            field = value
            updateNotification()
        }

    var isDeafened = false
        set(value) {
            field = value
            if (value) {
                isMicMuted = true
            }
            updateNotification()
        }

    inner class LocalBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        nativeInitJni()

        meshManager = MeshNetworkManager(this)
        meshManager?.onPeersChanged = { newPeers ->
            _peers.value = newPeers
            updateNotification()
        }

        meshManager?.onAudioPacketReceived = { data ->
            if (!isDeafened) {
                nativeInjectAudioPacket(data)
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_AUDIO -> {
                val secret = intent.getStringExtra(EXTRA_SECRET) ?: ""
                val nickname = intent.getStringExtra(EXTRA_NICKNAME) ?: "User"
                startAudio(secret, nickname)
            }

            ACTION_STOP_AUDIO -> stopAudio()
            ACTION_TOGGLE_MIC_MUTE -> toggleMicMute()
            ACTION_TOGGLE_DEAFEN -> toggleDeafen()
        }
        return START_STICKY
    }

    fun startAudio(secret: String, nickname: String) {
        if (_isAudioRunning.value) return

        meshManager?.start(secret, nickname)
        nativeStartAudio()
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
        stopSelf()
    }

    fun sendAudioPacket(data: ByteArray) {
        if (isMicMuted || isDeafened) return
        meshManager?.sendAudioPacket(data)
    }

    fun onNativeAudioData(data: ByteArray) {
        if (isMicMuted || isDeafened) return
        meshManager?.sendAudioPacket(data)
    }

    private fun toggleMicMute() {
        isMicMuted = !isMicMuted
    }

    private fun toggleDeafen() {
        isDeafened = !isDeafened
        if (!isDeafened) {
            isMicMuted = false
        }
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
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent =
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN_APP,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        val peerCount = _peers.value.size
        val status = buildList {
            if (isMicMuted) add("Mic muted")
            if (isDeafened) add("Deafened")
        }.joinToString(" • ")

        val contentText =
            if (status.isBlank()) "Connected to $peerCount peers" else "Connected to $peerCount peers • $status"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intercom Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_logo_custom)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(
                if (isMicMuted) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_btn_speak_now,
                if (isMicMuted) "Unmute" else "Mute",
                buildServiceActionPendingIntent(ACTION_TOGGLE_MIC_MUTE, REQUEST_TOGGLE_MIC)
            )
            .addAction(
                if (isDeafened) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode,
                if (isDeafened) "Undeafen" else "Deafen",
                buildServiceActionPendingIntent(ACTION_TOGGLE_DEAFEN, REQUEST_TOGGLE_DEAFEN)
            )
            .addAction(
                android.R.drawable.ic_lock_power_off,
                "Power Off",
                buildServiceActionPendingIntent(ACTION_STOP_AUDIO, REQUEST_POWER_OFF)
            )
            .build()
    }

    private fun buildServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MeshService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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

    external fun nativeStartAudio()
    external fun nativeStopAudio()
    external fun nativeInjectAudioPacket(data: ByteArray)
    external fun nativeInitJni()

    companion object {
        const val CHANNEL_ID = "MeshIntercomChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START_AUDIO = "ACTION_START"
        const val ACTION_STOP_AUDIO = "ACTION_STOP"
        const val ACTION_TOGGLE_MIC_MUTE = "ACTION_TOGGLE_MIC_MUTE"
        const val ACTION_TOGGLE_DEAFEN = "ACTION_TOGGLE_DEAFEN"

        const val EXTRA_SECRET = "EXTRA_SECRET"
        const val EXTRA_NICKNAME = "EXTRA_NICKNAME"

        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_TOGGLE_MIC = 101
        private const val REQUEST_TOGGLE_DEAFEN = 102
        private const val REQUEST_POWER_OFF = 103

        init {
            System.loadLibrary("meshintercom")
        }
    }
}
