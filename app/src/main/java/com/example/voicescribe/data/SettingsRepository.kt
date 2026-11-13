package com.example.voicescribe.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.AppThemePalette
import com.example.voicescribe.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val autoRecordOnLaunch: Boolean = true,
    val autoDeleteAudioAfterTranscription: Boolean = true,
    val speakerDiarizationEnabled: Boolean = true,
    val selectedProvider: AiProvider = AiProvider.GROQ,
    val selectedModel: String = "whisper-large-v3-turbo",
    val openaiApiKey: String = "",
    val groqApiKey: String = "",
    val geminiApiKey: String = "",
    val deepgramApiKey: String = "",
    val customBaseUrl: String = "http://10.0.2.2:8000/v1",
    val customApiKey: String = "",
    val customModelName: String = "whisper-1",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: AppThemePalette = AppThemePalette.DYNAMIC,
    val languageCode: String = "",
    val customPrompt: String = ""
)

class SettingsRepository(private val context: Context) {

    private object PrefKeys {
        val AUTO_RECORD = booleanPreferencesKey("auto_record_on_launch")
        val AUTO_DELETE_AUDIO = booleanPreferencesKey("auto_delete_audio")
        val SPEAKER_DIARIZATION = booleanPreferencesKey("speaker_diarization")
        val PROVIDER = stringPreferencesKey("ai_provider")
        val MODEL = stringPreferencesKey("ai_model")
        val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        val GROQ_KEY = stringPreferencesKey("groq_api_key")
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
        val DEEPGRAM_KEY = stringPreferencesKey("deepgram_api_key")
        val CUSTOM_URL= stringPreferencesKey("custom_base_url")
        val CUSTOM_KEY = stringPreferencesKey("custom_api_key")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val providerStr = prefs[PrefKeys.PROVIDER] ?: AiProvider.GROQ.name
        val provider = try { AiProvider.valueOf(providerStr) } catch (e: Exception) { AiProvider.GROQ}
        val themeModeStr = prefs[PrefKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try { ThemeMode.valueOf(themeModeStr) } catch (e: Exception) { ThemeMode.SYSTEM }
        val paletteStr = prefs[PrefKeys.THEME_PALETTE] ?: AppThemePalette.DYNAMIC.name
        val palette = try { AppThemePalette.valueOf(paletteStr) } catch (e: Exception) { AppThemePalette.DYNAMIC }

        AppSettings(
            autoRecordOnLaunch = prefs[PrefKeys.AUTO_RECORD] ?: true,
            autoDeleteAudioAfterTranscription = prefs[PrefKeys.AUTO_DELETE_AUDIO] ?: true,
            speakerDiarizationEnabled = prefs[PrefKeys.SPEAKER_DIARIZATION] ?: true,
            selectedProvider = provider,
            selectedModel = provider.normalizeModel(prefs[PrefKeys.MODEL] ?: provider.defaultModel),
            openaiApiKey = prefs[PrefKeys.OPENAI_KEY] ?: "",
            groqApiKey = prefs[PrefKeys.GROQ_KEY] ?: "",
            geminiApiKey = prefs[PrefKeys.GEMINI_KEY] ?: "",
            deepgramApiKey = prefs[PrefKeys.DEEPGRAM_KEY] ?: "",
            customBaseUrl = prefs[PrefKeys.CUSTOM_URL] ?: "http://10.0.2.2:8000/v1",
            customApiKey = prefs[PrefKeys.CUSTOM_KEY] ?: "",
            customModelName = prefs[PrefKeys.CUSTOM_MODEL] ?: "whisper-1",
            themeMode = themeMode,
            themePalette = palette,
            languageCode = prefs[PrefKeys.LANGUAGE_CODE] ?: "",
            customPrompt = prefs[PrefKeys.CUSTOM_PROMPT] ?: ""
        )
    }

    suspend fun setAutoRecordOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.AUTO_RECORD] = enabled }
    }

    suspend fun setAutoDeleteAudio(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.AUTO_DELETE_AUDIO] = enabled }
    }

    suspend fun setSpeakerDiarization(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.SPEAKER_DIARIZATION] = enabled }
    }

    suspend fun setProvider(provider: AiProvider, defaultModel: String = provider.defaultModel) {
        context.dataStore.edit {
            it[PrefKeys.PROVIDER] = provider.name
            it[PrefKeys.MODEL] = defaultModel
        }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[PrefKeys.MODEL] = model }
    }

    suspend fun setOpenAiKey(key: String) {
        context.dataStore.edit { it[PrefKeys.OPENAI_KEY] = key }
    }

    suspend fun setGroqKey(key: String) {
        context.dataStore.edit { it[PrefKeys.GROQ_KEY] = key }
    }

    suspend fun setGeminiKey(key: String) {
        context.dataStore.edit { it[PrefKeys.GEMINI_KEY] = key }
    }

    suspend fun setDeepgramKey(key: String) {
        context.dataStore.edit { it[PrefKeys.DEEPGRAM_KEY] = key }
    }

    suspend fun setCustomConfig(burl: String, key: String, model: String) {
        context.dataStore.edit {
            it[PrefKeys.CUSTOM_URL] = burl
            it[PrefKeys.CUSTOM_KEY] = key
            it[PrefKeys.CUSTOM_MODEL] = model
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[PrefKeys.THEME_MODE] = mode.name }
    }

    suspend fun setThemePalette(palette: AppThemePalette) {
        context.dataStore.edit { it[PrefKeys.THEME_PALETTE] = palette.name }
    }

    suspend fun setLanguageCode(lang: String) {
        context.dataStore.edit { it[PrefKeys.LANGUAGE_CODE] = lang }
    }
}
