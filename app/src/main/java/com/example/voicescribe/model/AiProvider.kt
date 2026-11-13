package com.example.voicescribe.model

import kotlinx.serialization.Serializable

@Serializable
enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val requiresApiKey: Boolean = true
) {
    OPENAI(
        displayName = "OpenAI Whisper",
        defaultModel = "whisper-1",
        availableModels = listOf("whisper-1")
    ),
    GROQ(
        displayName = "Groq Whisper (Ultra Fast)",
        defaultModel = "whisper-large-v3-turbo",
        availableModels = listOf("whisper-large-v3-turbo", "whisper-large-v3", "distil-whisper-large-v3-en")
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-3.6-flash",
        availableModels = listOf("gemini-3.6-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite")
    ),
    DEEPGRAM(
        displayName = "Deepgram (Nova-2 Diarization)",
        defaultModel = "nova-2",
        availableModels = listOf("nova-2", "nova-3", "enhanced")
    ),
    CUSTOM(
        displayName = "Custom OpenAI-Compatible Endpoint",
        defaultModel = "whisper-1",
        availableModels = listOf("whisper-1", "custom")
    );

    fun normalizeModel(model: String): String {
        val trimmed = model.trim()
        if (trimmed.isBlank()) return defaultModel
        if (this == GEMINI && trimmed in RETIRED_GEMINI_MODELS) {
            return defaultModel
        }
        return trimmed
    }

    companion object {
        val RETIRED_GEMINI_MODELS = setOf(
            "gemini-pro",
            "gemini-1.0-pro",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro",
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp",
            "gemini-2.0-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro",
            "gemini-3-pro-preview"
        )
    }
}
