package com.example.voicescribe.model

import kotlinx.serialization.Serializable

@Serializable
enum class RecordingStatus {
    RECORDED,
    TRANSCRIBING,
    TRANSCRIBED,
    ERROR
}

@Serializable
data class RecordingItem(
    val id: String,
    val title: String,
    val timestamp: Long,
    val durationMs: Long,
    val audioFilePath: String? = null,
    val isAudioDeleted: Boolean = false,
    val transcript: String? = null,
    val modelUsed: String? = null,
    val status: RecordingStatus = RecordingStatus.RECORDED,
    val errorMessage: String? = null,
    val fileSizeBytes: Long = 0L,
    val isRecovered: Boolean = false
)
