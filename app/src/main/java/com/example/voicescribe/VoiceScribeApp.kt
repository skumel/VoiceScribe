package com.example.voicescribe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.voicescribe.core.audio.CrashRecoveryManager
import com.example.voicescribe.data.RecordingRepository
import com.example.voicescribe.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceScribeApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var recordingRepository: RecordingRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var crashRecoveryManager: CrashRecoveryManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        settingsRepository = SettingsRepository(this)
        recordingRepository = RecordingRepository(this)
        crashRecoveryManager = CrashRecoveryManager(this, recordingRepository)

        createNotificationChannels()

        // Check for crashed / unfinalized recordings on startup
        applicationScope.launch {
            crashRecoveryManager.recoverUnfinishedRecordings()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordingChannel = NotificationChannel(
                CHANNEL_RECORDING_ID,
                "Audio Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing recording status and quick controls"
                setShowBadge(false)
            }

            val transcriptionChannel = NotificationChannel(
                CHANNEL_TRANSCRIPTION_ID,
                "AI Transcription Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status of AI voice transcription"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(recordingChannel)
            notificationManager.createNotificationChannel(transcriptionChannel)
        }
    }

    companion object {
        const val CHANNEL_RECORDING_ID = "channel_recording_service"
        const val CHANNEL_TRANSCRIPTION_ID = "channel_transcription_service"
        lateinit var instance: VoiceScribeApp
            private set
    }
}