package com.example.voicescribe.data

import android.content.Context
import android.util.AtomicFile
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class RecordingRepository(private val context: Context) {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val file = File(context.filesDir, "recordings.json")
    private val atomicFile = AtomicFile(file)

    private val _recordingsFlow = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordingsFlow: StateFlow<List<RecordingItem>> = _recordingsFlow.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!file.exists()) {
            _recordingsFlow.value = emptyList()
            return
        }
        try {
            val bytes = atomicFile.readFully()
            val str = String(bytes, Charsets.UTF_8)
            val items: List<RecordingItem> = json.decodeFromString(str)
            _recordingsFlow.value = items.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            _recordingsFlow.value = emptyList()
        }
    }

    private suspend fun saveToDisk() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val str = json.encodeToString(_recordingsFlow.value)
                    val bytes = str.toByteArray(Charsets.UTF_8)
                    val os = atomicFile.startWrite()
                    os.write(bytes)
                    atomicFile.finishWrite(os)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun addRecording(item: RecordingItem) {
        val current = _recordingsFlow.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        _recordingsFlow.value = current.sortedByDescending { it.timestamp }
        saveToDisk()
    }

    suspend fun updateRecording(item: RecordingItem) {
        val current = _recordingsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            current[index] = item
            _recordingsFlow.value = current
            saveToDisk()
        } else {
            addRecording(item)
        }
    }

    suspend fun deleteRecording(id: String, deleteFileFromDisk: Boolean = true) {
        val item = _recordingsFlow.value.find { it.id == id }
        if (deleteFileFromDisk && item != null && item.audioFilePath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val audioFile = File(item.audioFilePath)
                    if (audioFile.exists()) audioFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        val current = _recordingsFlow.value.filter { it.id != id }
        _recordingsFlow.value = current
        saveToDisk()
    }

    suspend fun deleteAudioOnly(id: String) {
        val item = _recordingsFlow.value.find { it.id == id } ?: return
        if (item.audioFilePath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val audioFile = File(item.audioFilePath)
                    if (audioFile.exists()) audioFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        updateRecording(item.copy(isAudioDeleted = true, audioFilePath = null))
    }

    fun getRecording(id: String): RecordingItem? {
        return _recordingsFlow.value.find { it.id == id }
    }
}