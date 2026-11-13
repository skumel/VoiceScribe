package com.example.voicescribe.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicescribe.VoiceScribeApp
import com.example.voicescribe.core.audio.AudioRecordingService
import com.example.voicescribe.core.audio.RecordingServiceState
import com.example.voicescribe.core.audio.ServiceRecordingStatus
import com.example.voicescribe.core.transcription.AiTranscriptionEngine
import com.example.voicescribe.data.AppSettings
import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.RecordingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TranscriptionStatusUi(
    val isTranscribing: Boolean = false,
    val targetItem: RecordingItem? = null,
    val progressMessage: String = "",
    val error: String? = null,
    val completedTranscript: String? = null
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VoiceScribeApp
    private val settingsRepo = app.settingsRepository
    private val recordingRepo = app.recordingRepository
    private val aiEngine = AiTranscriptionEngine(application, recordingRepo)

    val serviceState: StateFlow<RecordingServiceState> = AudioRecordingService.serviceState

    val settingsState: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _transcriptionStatus = MutableStateFlow(TranscriptionStatusUi())
    val transcriptionStatus: StateFlow<TranscriptionStatusUi> = _transcriptionStatus.asStateFlow()

    private var hasAutoRecordedThisLaunch = false

    init {
        // Listen for finalized recording events from the service
        viewModelScope.launch {
            AudioRecordingService.recordingFinalizedEvents.collect { item ->
                transcribeItem(item)
            }
        }
    }

    fun onPermissionGranted() {
        viewModelScope.launch {
            if (!hasAutoRecordedThisLaunch) {
                hasAutoRecordedThisLaunch = true
                val settings = settingsRepo.settingsFlow.first()
                if (settings.autoRecordOnLaunch && serviceState.value.status == ServiceRecordingStatus.IDLE) {
                    startRecording()
                }
            }
        }
    }

    fun startRecording(title: String? = null) {
        AudioRecordingService.start(app, title)
    }

    fun pauseRecording() {
        AudioRecordingService.pause(app)
    }

    fun resumeRecording() {
        AudioRecordingService.resume(app)
    }

    fun stopAndTranscribe() {
        AudioRecordingService.stopAndTranscribe(app)
    }

    fun stopAndSave() {
        AudioRecordingService.stopAndSave(app)
    }

    fun discard() {
        AudioRecordingService.discard(app)
    }

    fun updateSelectedProvider(provider: AiProvider) {
        viewModelScope.launch {
            settingsRepo.setProvider(provider)
        }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch {
            settingsRepo.setModel(model)
        }
    }

    private fun transcribeItem(item: RecordingItem) {
        viewModelScope.launch {
            val settings = settingsRepo.settingsFlow.first()
            _transcriptionStatus.value = TranscriptionStatusUi(
                isTranscribing = true,
                targetItem = item,
                progressMessage = "Transcribing with ${settings.selectedProvider.displayName}...",
                error = null,
                completedTranscript = null
            )

            val result = aiEngine.transcribeRecording(item, settings)
            when (result) {
                is AiTranscriptionEngine.TranscriptionResult.Success -> {
                    _transcriptionStatus.value = TranscriptionStatusUi(
                        isTranscribing = false,
                        targetItem = item,
                        progressMessage = "Transcription Complete!",
                        error = null,
                        completedTranscript = result.text
                    )
                }
                is AiTranscriptionEngine.TranscriptionResult.Error -> {
                    _transcriptionStatus.value = TranscriptionStatusUi(
                        isTranscribing = false,
                        targetItem = item,
                        progressMessage = "Transcription Failed",
                        error = result.message,
                        completedTranscript = null
                    )
                }
            }
        }
    }

    fun dismissTranscriptionDialog() {
        _transcriptionStatus.value = TranscriptionStatusUi()
    }
}