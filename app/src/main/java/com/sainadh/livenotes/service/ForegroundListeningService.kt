package com.sainadh.livenotes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sainadh.livenotes.LiveNotesApplication
import com.sainadh.livenotes.MainActivity
import com.sainadh.livenotes.R
import com.sainadh.livenotes.audio.BluetoothAudioRouter
import com.sainadh.livenotes.stt.AndroidSpeechTranscriber
import com.sainadh.livenotes.stt.NemotronTranscriber
import com.sainadh.livenotes.stt.SpeechTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object ServiceStateTracker {
    val listening = MutableStateFlow(false)
    val latestTranscript = MutableStateFlow("")
    val audioRoute = MutableStateFlow("Not listening")
    val lastSummaryError = MutableStateFlow<String?>(null)
}

class ForegroundListeningService : Service(), SpeechTranscriber.Listener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transcriber: SpeechTranscriber? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var bluetoothAudioRouter: BluetoothAudioRouter? = null
    private var usingNemotron = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bluetoothAudioRouter = BluetoothAudioRouter(this)
        selectTranscriber()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopListeningAndSelf()
            else -> startListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun selectTranscriber() {
        transcriber?.destroy()
        val app = application as LiveNotesApplication
        val downloadedModel = app.appContainer.modelDownloadManager.findAnyDownloaded()
        if (downloadedModel != null) {
            usingNemotron = true
            transcriber = NemotronTranscriber(
                context = this,
                listener = this,
                modelPath = downloadedModel.file.absolutePath
            )
        } else {
            usingNemotron = false
            transcriber = AndroidSpeechTranscriber(this, this)
        }
    }

    private fun startListening() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_listening_title), "Preparing microphone")
        )
        ServiceStateTracker.listening.value = true
        ServiceStateTracker.lastSummaryError.value = null
        val app = application as LiveNotesApplication
        val selectedInputMode = app.appContainer.secureSettings.readAudioInputMode()
        val resolvedRoute = bluetoothAudioRouter?.activate(selectedInputMode) ?: "Phone microphone"
        ServiceStateTracker.audioRoute.value = resolvedRoute
        acquireWakeLock()
        selectTranscriber()
        transcriber?.start()
        val engineLabel = if (usingNemotron) "Nemotron local model" else "Android speech recognizer"
        updateNotification("Using $engineLabel on $resolvedRoute")
    }

    private fun stopListeningAndSelf() {
        ServiceStateTracker.listening.value = false
        ServiceStateTracker.latestTranscript.value = ""
        ServiceStateTracker.audioRoute.value = "Not listening"
        transcriber?.stop()
        bluetoothAudioRouter?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        transcriber?.destroy()
        bluetoothAudioRouter?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        serviceScope.cancel()
        ServiceStateTracker.listening.value = false
        ServiceStateTracker.audioRoute.value = "Not listening"
        super.onDestroy()
    }

    override fun onTranscript(text: String, isFinal: Boolean) {
        ServiceStateTracker.latestTranscript.value = text
        serviceScope.launch {
            val app = application as LiveNotesApplication
            val result = app.appContainer.conversationOrchestrator.onTranscript(text, isFinal)
            result.fold(
                onSuccess = { ServiceStateTracker.lastSummaryError.value = null },
                onFailure = { error ->
                    val message = error.message ?: error.javaClass.simpleName
                    ServiceStateTracker.lastSummaryError.value = message
                    updateNotification("Summary error: $message")
                }
            )
        }
        updateNotification(text)
    }

    override fun onStateChanged(state: String) {
        updateNotification(state)
    }

    override fun onError(reason: String) {
        ServiceStateTracker.lastSummaryError.value = reason
        updateNotification(reason)
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(getString(R.string.notification_listening_title), content))
    }

    private fun buildNotification(title: String, content: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ForegroundListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(content.ifBlank { "Listening in background" })
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "livenotes:transcription").apply {
            acquire()
        }
    }

    companion object {
        private const val CHANNEL_ID = "live-notes-listening"
        private const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.sainadh.livenotes.action.START"
        const val ACTION_STOP = "com.sainadh.livenotes.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ForegroundListeningService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ForegroundListeningService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
