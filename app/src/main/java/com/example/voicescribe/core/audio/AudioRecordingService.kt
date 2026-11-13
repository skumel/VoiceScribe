package com.example.voicescribe.core.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.voicescribe.MainActivity
import com.example.voicescribe.R
import com.example.voicescribe.VoiceScribeApp
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ServiceRecordingStatus {
    IDLE,
    RECORDING,
    PAUSED
}

data class RecordingServiceState(
    val status: ServiceRecordingStatus = ServiceRecordingStatus.IDLE,
    val durationMs: Long = 0L,
    val currentAmplitude: Float = 0f,
    val activeSessionId: String? = null,
    val activeFilePath: String? = null
)

class AudioRecordingService : Service() {

    private val tag = "AudioRecordingService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private lateinit var recorderManager: AudioRecorderManager
    private val recoveryManager get() = VoiceScribeApp.instance.crashRecoveryManager
    private val repository get() = VoiceScribeApp.instance.recordingRepository

    private var currentSessionId: String? = null
    private var currentFile: File? = null
    private var currentTitle: String = ""
    private var startTimeMillis: Long = 0L
    private var accumulatedDurationMs: Long = 0L
    private var lastResumeTimeMillis: Long = 0L

    override fun onCreate() {
        super.onCreate()
        recorderManager = AudioRecorderManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE)
                startRecordingSession(title)
            }
            ACTION_PAUSE -> pauseRecordingSession()
            ACTION_RESUME -> resumeRecordingSession()
            ACTION_STOP_AND_TRANSCRIBE -> stopRecordingSession(shouldTranscribe = true)
            ACTION_STOP_AND_SAVE -> stopRecordingSession(shouldTranscribe = false)
            ACTION_DISCARD -> discardRecordingSession()
        }

        return START_NOT_STICKY
    }

    private fun startRecordingSession(customTitle: String?) {
        if (_serviceState.value.status != ServiceRecordingStatus.IDLE) return

        val sessionId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp))
        val title = customTitle ?: "Recording $dateFormatted"

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val targetFile = File(recordingsDir, "rec_$sessionId.m4a")

        if (recorderManager.startRecording(targetFile)) {
            currentSessionId = sessionId
            currentFile = targetFile
            currentTitle = title
            startTimeMillis = timestamp
            lastResumeTimeMillis = timestamp
            accumulatedDurationMs = 0L

            val notification = buildNotification("Recording: $title", "00:00", isRecording = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            serviceScope.launch {
                recoveryManager.startSession(sessionId, title, targetFile.absolutePath, timestamp)
            }

            _serviceState.value = RecordingServiceState(
                status = ServiceRecordingStatus.RECORDING,
                durationMs = 0L,
                currentAmplitude = 0f,
                activeSessionId = sessionId,
                activeFilePath = targetFile.absolutePath
            )

            startTicker()
        } else {
            Log.e(tag, "Failed to start MediaRecorder")
            stopSelf()
        }
    }

    private fun pauseRecordingSession() {
        if (_serviceState.value.status == ServiceRecordingStatus.RECORDING) {
            if (recorderManager.pauseRecording()) {
                accumulatedDurationMs += (System.currentTimeMillis() - lastResumeTimeMillis)
                _serviceState.value = _serviceState.value.copy(
                    status = ServiceRecordingStatus.PAUSED,
                    currentAmplitude = 0f
                )
                updateNotification()
            }
        }
    }

    private fun resumeRecordingSession() {
        if (_serviceState.value.status == ServiceRecordingStatus.PAUSED) {
            if (recorderManager.resumeRecording()) {
                lastResumeTimeMillis = System.currentTimeMillis()
                _serviceState.value = _serviceState.value.copy(
                    status = ServiceRecordingStatus.RECORDING
                )
                updateNotification()
            }
        }
    }

    private fun stopRecordingSession(shouldTranscribe: Boolean) {
        if (_serviceState.value.status == ServiceRecordingStatus.IDLE) return

        tickerJob?.cancel()
        recorderManager.stopRecording()

        val totalDuration = if (_serviceState.value.status == ServiceRecordingStatus.RECORDING) {
            accumulatedDurationMs + (System.currentTimeMillis() - lastResumeTimeMillis)
        } else {
            accumulatedDurationMs
        }

        val file = currentFile
        val sessionId = currentSessionId ?: UUID.randomUUID().toString()
        val title = currentTitle

        serviceScope.launch {
            recoveryManager.clearActiveSession()

            if (file != null && file.exists() && file.length() > 0) {
                val item = RecordingItem(
                    id = sessionId,
                    title = title,
                    timestamp = startTimeMillis,
                    durationMs = totalDuration,
                    audioFilePath = file.absolutePath,
                    isAudioDeleted = false,
                    status = if (shouldTranscribe) RecordingStatus.TRANSCRIBING else RecordingStatus.RECORDED,
                    fileSizeBytes = file.length()
                )
                repository.addRecording(item)

                if (shouldTranscribe) {
                    _recordingFinalizedEvents.emit(item)
                }
            }

            resetState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun discardRecordingSession() {
        tickerJob?.cancel()
        recorderManager.stopRecording()

        val file = currentFile
        serviceScope.launch {
            recoveryManager.clearActiveSession()
            if (file != null && file.exists()) {
                file.delete()
            }
            resetState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun resetState() {
        currentSessionId = null
        currentFile = null
        currentTitle = ""
        startTimeMillis = 0L
        accumulatedDurationMs = 0L
        _serviceState.value = RecordingServiceState(
            status = ServiceRecordingStatus.IDLE,
            durationMs = 0L,
            currentAmplitude = 0f
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var updateCounter = 0
            while (isActive) {
                delay(100)
                if (_serviceState.value.status == ServiceRecordingStatus.RECORDING) {
                    val currentDuration = accumulatedDurationMs + (System.currentTimeMillis() - lastResumeTimeMillis)
                    val rawAmp = recorderManager.getMaxAmplitude()
                    val normalizedAmp = (rawAmp / 32767f).coerceIn(0f, 1f)

                    _serviceState.value = _serviceState.value.copy(
                        durationMs = currentDuration,
                        currentAmplitude = normalizedAmp
                    )

                    updateCounter++
                    // Update notification & crash session once per second
                    if (updateCounter % 10 == 0) {
                        updateNotification()
                        recoveryManager.updateSessionDuration(currentDuration)
                    }
                }
            }
        }
    }

    private fun updateNotification() {
        val durationFormatted = formatDuration(_serviceState.value.durationMs)
        val isRecording = _serviceState.value.status == ServiceRecordingStatus.RECORDING
        val title = if (isRecording) "Recording Voice..." else "Recording Paused"
        val notification = buildNotification(title, durationFormatted, isRecording)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, durationText: String, isRecording: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = if (isRecording) ACTION_PAUSE else ACTION_RESUME
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopTranscribeIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_STOP_AND_TRANSCRIBE
        }
        val stopTranscribePendingIntent = PendingIntent.getService(
            this,
            2,
            stopTranscribeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            if (isRecording) "Pause" else "Resume",
            pauseResumePendingIntent
        ).build()

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Stop & Transcribe",
            stopTranscribePendingIntent
        ).build()

        return NotificationCompat.Builder(this, VoiceScribeApp.CHANNEL_RECORDING_ID)
            .setContentTitle(title)
            .setContentText(durationText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun formatDuration(millis: Long): String {
        val totalSecs = millis / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        recorderManager.stopRecording()
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.voicescribe.action.START"
        const val ACTION_PAUSE = "com.example.voicescribe.action.PAUSE"
        const val ACTION_RESUME = "com.example.voicescribe.action.RESUME"
        const val ACTION_STOP_AND_TRANSCRIBE = "com.example.voicescribe.action.STOP_AND_TRANSCRIBE"
        const val ACTION_STOP_AND_SAVE = "com.example.voicescribe.action.STOP_AND_SAVE"
        const val ACTION_DISCARD = "com.example.voicescribe.action.DISCARD"

        const val EXTRA_TITLE = "extra_title"

        private val _serviceState = MutableStateFlow(RecordingServiceState())
        val serviceState: StateFlow<RecordingServiceState> = _serviceState.asStateFlow()

        private val _recordingFinalizedEvents = MutableSharedFlow<RecordingItem>(extraBufferCapacity = 1)
        val recordingFinalizedEvents: SharedFlow<RecordingItem> = _recordingFinalizedEvents.asSharedFlow()

        fun start(context: Context, title: String? = null) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START
                if (title != null) putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply { action = ACTION_RESUME }
            context.startService(intent)
        }

        fun stopAndTranscribe(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply { action = ACTION_STOP_AND_TRANSCRIBE }
            context.startService(intent)
        }

        fun stopAndSave(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply { action = ACTION_STOP_AND_SAVE }
            context.startService(intent)
        }

        fun discard(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply { action = ACTION_DISCARD }
            context.startService(intent)
        }
    }
}