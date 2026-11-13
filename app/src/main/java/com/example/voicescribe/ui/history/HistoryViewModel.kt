package com.example.voicescribe.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicescribe.VoiceScribeApp
import com.example.voicescribe.core.audio.AudioPlayer
import com.example.voicescribe.core.audio.PlayerState
import com.example.voicescribe.core.transcription.AiTranscriptionEngine
import com.example.voicescribe.data.AppSettings
import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    TRANSCRIBED,
    AUDIO_ONLY
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VoiceScribeApp
    private val recordingRepo = app.recordingRepository
    private val settingsRepo = app.settingsRepository
    private val aiEngine = AiTranscriptionEngine(application, recordingRepo)
    val audioPlayer = AudioPlayer(viewModelScope)

    val playerState: StateFlow<PlayerState> = audioPlayer.playerState

    val settingsState: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    private val _selectedItemForDetail = MutableStateFlow<RecordingItem?>(null)
    val selectedItemForDetail: StateFlow<RecordingItem?> = _selectedItemForDetail.asStateFlow()

    private val _isTranscribingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isTranscribingMap: StateFlow<Map<String, Boolean>> = _isTranscribingMap.asStateFlow()

    val filteredRecordings: StateFlow<List<RecordingItem>> = combine(
        recordingRepo.recordingsFlow,
        _searchQuery,
        _selectedFilter
    ) { recordings, query, filter ->
        recordings.filter { item ->
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.TRANSCRIBED -> !item.transcript.isNullOrBlank()
                HistoryFilter.AUDIO_ONLY -> !item.isAudioDeleted && item.audioFilePath != null
            }
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    (item.transcript?.contains(query, ignoreCase = true) == true)

            matchesFilter && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    fun selectItemForDetail(item: RecordingItem?) {
        _selectedItemForDetail.value = item
    }

    fun playAudio(filePath: String) {
        audioPlayer.play(filePath)
    }

    fun pauseAudio() {
        audioPlayer.pause()
    }

    fun seekAudio(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun deleteRecording(id: String) {
        viewModelScope.launch {
            if (_selectedItemForDetail.value?.id == id) {
                _selectedItemForDetail.value = null
            }
            audioPlayer.stop()
            recordingRepo.deleteRecording(id)
        }
    }

    fun deleteAudioOnly(id: String) {
        viewModelScope.launch {
            audioPlayer.stop()
            recordingRepo.deleteAudioOnly(id)
            _selectedItemForDetail.value = recordingRepo.getRecording(id)
        }
    }

    fun transcribeRecording(item: RecordingItem, overrideProvider: AiProvider? = null, overrideModel: String? = null) {
        viewModelScope.launch {
            _isTranscribingMap.value = _isTranscribingMap.value + (item.id to true)
            val settings = settingsRepo.settingsFlow.first()
            aiEngine.transcribeRecording(item, settings, overrideProvider, overrideModel)
            _isTranscribingMap.value = _isTranscribingMap.value - item.id
            _selectedItemForDetail.value = recordingRepo.getRecording(item.id)
        }
    }

    fun scanForRecoveredRecordings() {
        viewModelScope.launch {
            app.crashRecoveryManager.recoverUnfinishedRecordings()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
}