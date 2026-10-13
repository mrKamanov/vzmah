package com.vzmah.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.vzmah.app.MainActivity
import com.vzmah.app.R
import com.vzmah.app.VzmahApp
import com.vzmah.app.data.bluetooth.HidState
import com.vzmah.app.data.sensor.GestureSensorManager
import com.vzmah.app.data.bluetooth.isReady
import com.vzmah.app.domain.model.CommandRequest
import com.vzmah.app.domain.model.PresentationCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PresentationService : LifecycleService() {

    private var container: com.vzmah.app.AppContainer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as? VzmahApp)?.container
        createNotificationChannel()
        startInForeground(status.value)

        container?.let { deps ->
            deps.hidManager.register()
            deps.hidManager.refreshHosts()
            deps.sensorManager.acquire(GestureSensorManager.OWNER_SERVICE)
            lifecycleScope.launch {
                deps.hidManager.state.collect { hid ->
                    status.value = status.value.copy(
                        hostName = when (hid) {
                            is HidState.Connected -> hid.hostName
                            is HidState.Connecting -> hid.hostName
                            else -> null
                        },
                        connected = hid.isReady,
                    )
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PREVIOUS -> dispatch(PresentationCommand.PreviousSlide)
            ACTION_NEXT -> dispatch(PresentationCommand.NextSlide)
            ACTION_BLANK -> dispatch(PresentationCommand.ToggleBlank)
        }
        startInForeground(status.value)
        acquireWakeLock()
        return START_STICKY
    }

    private fun dispatch(command: PresentationCommand) {
        val sender = container?.sendHidCommand ?: return
        lifecycleScope.launch {
            if (sender(CommandRequest(command)) && command is PresentationCommand.ToggleBlank) {
                _blankActive.value = !_blankActive.value
            }
        }
    }

    override fun onDestroy() {
        container?.sensorManager?.release(GestureSensorManager.OWNER_SERVICE)
        releaseWakeLock()
        status.value = ServiceStatus()
        super.onDestroy()
    }

    private fun startInForeground(state: ServiceStatus) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: ServiceStatus): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when {
            !state.connected && state.hostName != null -> getString(R.string.service_status_connecting, state.hostName)
            !state.connected -> getString(R.string.service_status_waiting)
            else -> getString(R.string.service_status_ready, state.hostName.orEmpty(), formatElapsed(state.elapsedSeconds))
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vzmah)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notif_prev), notificationAction(ACTION_PREVIOUS))
            .addAction(0, getString(R.string.notif_blank), notificationAction(ACTION_BLANK))
            .addAction(0, getString(R.string.notif_next), notificationAction(ACTION_NEXT))
            .build()
    }

    private fun notificationAction(action: String): PendingIntent {
        val intent = Intent(this, PresentationService::class.java).setAction(action)
        return PendingIntent.getForegroundService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(status.value))
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "vzmah_presentation"
        private const val NOTIFICATION_ID = 0x5647
        private const val WAKE_LOCK_TAG = "Vzmah::Presentation"

        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000

        private const val ACTION_PREVIOUS = "com.vzmah.app.action.PREVIOUS"
        private const val ACTION_NEXT = "com.vzmah.app.action.NEXT"
        private const val ACTION_BLANK = "com.vzmah.app.action.BLANK"

        private val status = MutableStateFlow(ServiceStatus())

        val serviceStatus: StateFlow<ServiceStatus> = status.asStateFlow()

        private val _blankActive = MutableStateFlow(false)

        val blankActive: StateFlow<Boolean> = _blankActive.asStateFlow()

        fun publishBlank(active: Boolean) {
            _blankActive.value = active
        }

        fun start(context: Context) {
            val intent = Intent(context, PresentationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PresentationService::class.java))
        }

        fun publishElapsed(elapsedSeconds: Long) {
            status.value = status.value.copy(elapsedSeconds = elapsedSeconds)
        }

        fun formatElapsed(totalSeconds: Long): String {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}

data class ServiceStatus(
    val hostName: String? = null,
    val connected: Boolean = false,
    val elapsedSeconds: Long = 0L,
)
