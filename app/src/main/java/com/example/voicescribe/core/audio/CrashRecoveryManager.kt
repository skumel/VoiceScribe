package com.example.voicescribe.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.voicescribe.data.RecordingRepository
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class ActiveRecordingSession(
    val id: String,
    val title: String,
    val startTime: Long,
    val filePath: String,
    val lastUpdatedDurationMs: Long = 0L
)

class CrashRecoveryManager(
    private val context: Context,
    private val repository: RecordingRepository
) {
    private val tag = "CrashRecoveryManager"
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionFile = File(context.filesDir, "active_session.json")

    suspend fun startSession(id: String, title: String, filePath: String, startTime: Long) {
        withContext(Dispatchers.IO) {
            try {
                val session = ActiveRecordingSession(
                    id = id,
                    title = title,
                    startTime = startTime,
                    filePath = filePath,
                    lastUpdatedDurationMs = 0L
                )
                sessionFile.writeText(json.encodeToString(session))
            } catch (e: Exception) {
                Log.e(tag, "Failed to start active session tracking", e)
            }
        }
    }

    suspend fun updateSessionDuration(durationMs: Long) {
        withContext(Dispatchers.IO) {
            try {
                if (sessionFile.exists()) {
                    val content = sessionFile.readText()
                    val current = json.decodeFromString<ActiveRecordingSession>(content)
                    val updated = current.copy(lastUpdatedDurationMs = durationMs)
                    sessionFile.writeText(json.encodeToString(updated))
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to update active session duration", e)
            }
        }
    }

    suspend fun clearActiveSession() {
        withContext(Dispatchers.IO) {
            try {
                if (sessionFile.exists()) {
                    sessionFile.delete()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to clear active session", e)
            }
        }
    }

    suspend fun recoverUnfinishedRecordings(): List<RecordingItem> {
        return withContext(Dispatchers.IO) {
            val recoveredItems = mutableListOf<RecordingItem>()

            // 1. Check active session file
            if (sessionFile.exists()) {
                try {
                    val content = sessionFile.readText()
                    val session = json.decodeFromString<ActiveRecordingSession>(content)
                    val audioFile = File(session.filePath)

                    if (audioFile.exists() && audioFile.length() > 0) {
                        val duration = getAudioDuration(audioFile) ?: session.lastUpdatedDurationMs
                        val item = RecordingItem(
                            id = session.id,
                            title = "Recovered: ${session.title}",
                            timestamp = session.startTime,
                            durationMs = if (duration > 0) duration else 1000L,
                            audioFilePath = audioFile.absolutePath,
                            isAudioDeleted = false,
                            status = RecordingStatus.RECORDED,
                            fileSizeBytes = audioFile.length(),
                            isRecovered = true
                        )
                        repository.addRecording(item)
                        recoveredItems.add(item)
                        Log.i(tag, "Recovered active session recording: ${item.title}")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error recovering active session", e)
                } finally {
                    sessionFile.delete()
                }
            }

            // 2. Scan audio directory for any orphaned audio files not yet indexed
            try {
                val recordingsDir = File(context.filesDir, "recordings")
                if (recordingsDir.exists()) {
                    val existingPaths = repository.recordingsFlow.value.mapNotNull { it.audioFilePath }.toSet()
                    val files = recordingsDir.listFiles() ?: emptyArray()

                    for (file in files) {
                        if (file.isFile && file.length() > 0 && !existingPaths.contains(file.absolutePath)) {
                            val duration = getAudioDuration(file) ?: 0L
                            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                            val item = RecordingItem(
                                id = "recovered_${file.nameWithoutExtension}_${System.currentTimeMillis()}",
                                title = "Recovered File ($dateStr)",
                                timestamp = file.lastModified(),
                                durationMs = duration,
                                audioFilePath = file.absolutePath,
                                isAudioDeleted = false,
                                status = RecordingStatus.RECORDED,
                                fileSizeBytes = file.length(),
                                isRecovered = true
                            )
                            repository.addRecording(item)
                            recoveredItems.add(item)
                            Log.i(tag, "Recovered orphaned audio file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error scanning orphaned audio files", e)
            }

            recoveredItems
        }
    }

    private fun getAudioDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }
}