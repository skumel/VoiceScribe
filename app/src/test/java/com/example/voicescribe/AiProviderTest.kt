package com.example.voicescribe

import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.AppThemePalette
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import com.example.voicescribe.model.ThemeMode
import org.junit.Assert.*
import org.junit.Test

class AiProviderTest {

    @Test
    fun testProviderDefaults() {
        assertEquals("whisper-1", AiProvider.OPENAI.defaultModel)
        assertEquals("whisper-large-v3-turbo", AiProvider.GROQ.defaultModel)
        assertEquals("gemini-3.6-flash", AiProvider.GEMINI.defaultModel)
        assertEquals("nova-2", AiProvider.DEEPGRAM.defaultModel)
        assertEquals("whisper-1", AiProvider.CUSTOM.defaultModel)
    }

    @Test
    fun testGeminiModelsExcludeRetiredAndNormalizeLegacy() {
        val geminiModels = AiProvider.GEMINI.availableModels
        assertEquals(listOf("gemini-3.6-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite"), geminiModels)
        AiProvider.RETIRED_GEMINI_MODELS.forEach { retired ->
            assertFalse("Retired model $retired should not be in availableModels", retired in geminiModels)
            assertEquals("gemini-3.6-flash", AiProvider.GEMINI.normalizeModel(retired))
        }
        assertEquals("gemini-3.6-flash", AiProvider.GEMINI.normalizeModel(""))
        assertEquals("gemini-3.8-flash", AiProvider.GEMINI.normalizeModel("gemini-3.8-flash"))
        assertEquals("gemini-3.5-flash-lite", AiProvider.GEMINI.normalizeModel("gemini-3.5-flash-lite"))
    }

    @Test
    fun testRecordingItemSerializationModel() {
        val item = RecordingItem(
            id = "test-123",
            title = "Meeting notes",
            timestamp = 1700000000000L,
            durationMs = 65000L,
            status = RecordingStatus.RECORDED,
            fileSizeBytes = 102400L
        )
        assertEquals("test-123", item.id)
        assertEquals(false, item.isAudioDeleted)
        assertNull(item.transcript)
    }

    @Test
    fun testThemeSettingsEnumValues() {
        assertEquals(3, ThemeMode.values().size)
        assertTrue(AppThemePalette.values().size >= 6)
        assertEquals(0xFF0288D1, AppThemePalette.OCEAN_BLUE.primaryHex)
    }
}