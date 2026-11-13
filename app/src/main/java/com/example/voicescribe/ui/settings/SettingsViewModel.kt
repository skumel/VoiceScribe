package com.example.voicescribe.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicescribe.VoiceScribeApp
import com.example.voicescribe.core.transcription.AiTranscriptionEngine
import com.example.voicescribe.data.AppSettings
import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.AppThemePalette
import com.example.voicescribe.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VoiceScribeApp
    private val settingsRepo = app.settingsRepository
    private val recordingRepo = app.recordingRepository
    private val aiEngine = AiTranscriptionEngine(application, recordingRepo)

    val settingsState: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    fun setAutoRecord(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoRecordOnLaunch(enabled) }
    }

    fun setAutoDeleteAudio(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoDeleteAudio(enabled) }
    }

    fun setSpeakerDiarization(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setSpeakerDiarization(enabled) }
    }

    fun setProvider(provider: AiProvider) {
        viewModelScope.launch { settingsRepo.setProvider(provider) }
    }

    fun setModel(model: String) {
        viewModelScope.launch { settingsRepo.setModel(model) }
    }

    fun setOpenAiKey(key: String) {
        viewModelScope.launch { settingsRepo.setOpenAiKey(key) }
    }

    fun setGroqKey(key: String) {
        viewModelScope.launch { settingsRepo.setGroqKey(key) }
    }

    fun setGeminiKey(key: String) {
        viewModelScope.launch { settingsRepo.setGeminiKey(key) }
    }

    fun setDeepgramKey(key: String) {
        viewModelScope.launch { settingsRepo.setDeepgramKey(key) }
    }

    fun setCustomConfig(baseUrl: String, key: String, model: String) {
        viewModelScope.launch { settingsRepo.setCustomConfig(baseUrl, key, model) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setThemePalette(palette: AppThemePalette) {
        viewModelScope.launch { settingsRepo.setThemePalette(palette) }
    }

    fun testConnection(provider: AiProvider) {
        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            val settings = settingsRepo.settingsFlow.first()
            val result = aiEngine.testProviderConnection(provider, settings)
            _testState.value = if (result.isSuccess) {
                ConnectionTestState.Success(result.getOrDefault("Success!"))
            } else {
                ConnectionTestState.Error(result.exceptionOrNull()?.message ?: "Failed to connect")
            }
        }
    }

    fun dismissTestDialog() {
        _testState.value = ConnectionTestState.Idle
    }
}