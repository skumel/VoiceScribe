package com.example.voicescribe.core.transcription

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.voicescribe.data.AppSettings
import com.example.voicescribe.data.RecordingRepository
import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class AiTranscriptionEngine(
    private val context: Context,
    private val repository: RecordingRepository
) {
    private val tag = "AiTranscriptionEngine"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    sealed class TranscriptionResult {
        data class Success(val text: String, val modelUsed: String) : TranscriptionResult()
        data class Error(val message: String) : TranscriptionResult()
    }

    suspend fun transcribeRecording(
        recording: RecordingItem,
        settings: AppSettings,
        overrideProvider: AiProvider? = null,
        overrideModel: String? = null
    ): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            val audioPath = recording.audioFilePath
            if (audioPath == null) {
                val err = "Audio file path is missing or audio was deleted."
                repository.updateRecording(recording.copy(status = RecordingStatus.ERROR, errorMessage = err))
                return@withContext TranscriptionResult.Error(err)
            }

            val audioFile = File(audioPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                val err = "Audio file does not exist on disk."
                repository.updateRecording(recording.copy(status = RecordingStatus.ERROR, errorMessage = err))
                return@withContext TranscriptionResult.Error(err)
            }

            val provider = overrideProvider ?: settings.selectedProvider
            val model = overrideModel ?: settings.selectedModel

            repository.updateRecording(recording.copy(status = RecordingStatus.TRANSCRIBING, errorMessage = null))

            val result = when (provider) {
                AiProvider.OPENAI -> transcribeWithOpenAi(audioFile, model, settings.openaiApiKey, settings.languageCode, settings.customPrompt)
                AiProvider.GROQ -> transcribeWithGroq(audioFile, model, settings.groqApiKey, settings.languageCode, settings.customPrompt)
                AiProvider.GEMINI -> transcribeWithGemini(audioFile, model, settings.geminiApiKey, settings.customPrompt, settings.speakerDiarizationEnabled)
                AiProvider.DEEPGRAM -> transcribeWithDeepgram(audioFile, model, settings.deepgramApiKey, settings.languageCode, settings.speakerDiarizationEnabled)
                AiProvider.CUSTOM -> transcribeWithCustom(audioFile, settings.customBaseUrl, model, settings.customApiKey, settings.languageCode, settings.customPrompt)
            }

            when (result) {
                is TranscriptionResult.Success -> {
                    var updated = recording.copy(
                        transcript = result.text,
                        modelUsed = "${provider.displayName} (${result.modelUsed})",
                        status = RecordingStatus.TRANSCRIBED,
                        errorMessage = null
                    )

                    if (settings.autoDeleteAudioAfterTranscription) {
                        try {
                            if (audioFile.exists()) {
                                audioFile.delete()
                            }
                            updated = updated.copy(isAudioDeleted = true, audioFilePath = null)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to delete original audio file after transcription", e)
                        }
                    }

                    repository.updateRecording(updated)
                }
                is TranscriptionResult.Error -> {
                    repository.updateRecording(
                        recording.copy(
                            status = RecordingStatus.ERROR,
                            errorMessage = result.message
                        )
                    )
                }
            }

            result
        }
    }

    private fun transcribeWithOpenAi(
        audioFile: File,
        model: String,
        apiKey: String,
        language: String,
        prompt: String
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error("OpenAI API Key is not configured. Please add it in Settings.")
        }

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            .addFormDataPart("model", model.ifBlank { "whisper-1" })
            .addFormDataPart("response_format", "json")

        if (language.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("language", language.trim())
        }
        if (prompt.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("prompt", prompt.trim())
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(requestBodyBuilder.build())
            .build()

        return executeWhisperRequest(request, "OpenAI")
    }

    private fun transcribeWithGroq(
        audioFile: File,
        model: String,
        apiKey: String,
        language: String,
        prompt: String
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error("Groq API Key is not configured. Please add it in Settings.")
        }

        val chosenModel = if (model.isBlank()) "whisper-large-v3-turbo" else model

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            .addFormDataPart("model", chosenModel)
            .addFormDataPart("response_format", "json")

        if (language.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("language", language.trim())
        }
        if (prompt.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("prompt", prompt.trim())
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(requestBodyBuilder.build())
            .build()

        return executeWhisperRequest(request, "Groq")
    }

    private fun transcribeWithGemini(
        audioFile: File,
        model: String,
        apiKey: String,
        customPrompt: String,
        diarize: Boolean
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error("Gemini API Key is not configured. Please add it in Settings.")
        }

        val trimmedKey = apiKey.trim()
        val chosenModel = AiProvider.GEMINI.normalizeModel(model)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$chosenModel:generateContent?key=$trimmedKey"

        var uploadedFileName: String? = null
        try {
            val promptText = if (customPrompt.isNotBlank()) {
                customPrompt
            } else if (diarize) {
                "Please generate a complete, accurate transcript of this audio recording. Carefully separate and label each person speaking (e.g. 'Speaker 1:', 'Speaker 2:', or named participants if identifiable). Format each turn on a separate line with clear paragraphs. Do not add any introductory or concluding remarks."
            } else {
                "Please generate a complete, accurate, word-for-word transcript of this audio recording. Output only the transcript text with proper punctuation and paragraphs, with no extra conversational comments or markdown headers."
            }

            val escapedPromptJson = JsonPrimitive(promptText).toString()
            val mediaPartJson = if (audioFile.length() > GEMINI_INLINE_THRESHOLD_BYTES) {
                val uploaded = uploadFileToGemini(audioFile, trimmedKey)
                uploadedFileName = uploaded.name
                """{"file_data":{"mime_type":"audio/mp4","file_uri":${JsonPrimitive(uploaded.uri)}}}"""
            } else {
                val base64Audio = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
                """{"inline_data":{"mime_type":"audio/mp4","data":"$base64Audio"}}"""
            }

            val jsonPayload = """{"contents":[{"parts":[$mediaPartJson,{"text":$escapedPromptJson}]}]}"""

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val parsed = json.parseToJsonElement(body).jsonObject
                        parsed["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: body
                    } catch (e: Exception) {
                        body
                    }
                    return TranscriptionResult.Error("Gemini Error (${response.code}): $errMsg")
                }

                val parsed = json.parseToJsonElement(body).jsonObject
                val candidates = parsed["candidates"]?.jsonArray
                val firstCandidate = candidates?.getOrNull(0)?.jsonObject
                val content = firstCandidate?.get("content")?.jsonObject
                val parts = content?.get("parts")?.jsonArray
                val transcriptText = parts?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }?.joinToString("\n") ?: ""

                if (transcriptText.isBlank()) {
                    return TranscriptionResult.Error("Gemini returned an empty response.")
                }

                return TranscriptionResult.Success(transcriptText.trim(), chosenModel)
            }
        } catch (e: Exception) {
            Log.e(tag, "Gemini transcription failed", e)
            return TranscriptionResult.Error("Connection error: ${e.localizedMessage ?: "Unknown error"}")
        } finally {
            uploadedFileName?.let { deleteGeminiFile(it, trimmedKey) }
        }
    }

    private data class UploadedGeminiFile(val name: String, val uri: String)

    private fun uploadFileToGemini(audioFile: File, apiKey: String): UploadedGeminiFile {
        val fileLength = audioFile.length()
        val mimeType = "audio/mp4"
        val startBody = """{"file":{"display_name":${JsonPrimitive(audioFile.name)}}}"""
            .toRequestBody("application/json".toMediaType())

        val startRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", fileLength.toString())
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(startBody)
            .build()

        val uploadUrl = httpClient.newCall(startRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IllegalStateException("Gemini file upload init failed (${response.code}): $errBody")
            }
            response.header("X-Goog-Upload-Url")
                ?: throw IllegalStateException("Gemini file upload missing X-Goog-Upload-Url header")
        }

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("X-Goog-Upload-Command", "upload, finalize")
            .header("X-Goog-Upload-Offset", "0")
            .post(audioFile.asRequestBody(mimeType.toMediaType()))
            .build()

        val (fileName, fileUri, state) = httpClient.newCall(uploadRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini file upload failed (${response.code}): $body")
            }
            val fileObj = json.parseToJsonElement(body).jsonObject["file"]?.jsonObject
                ?: throw IllegalStateException("Gemini file upload response missing file metadata")
            val name = fileObj["name"]?.jsonPrimitive?.content ?: ""
            val uri = fileObj["uri"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Gemini file upload response missing uri")
            val state = fileObj["state"]?.jsonPrimitive?.content ?: "ACTIVE"
            Triple(name, uri, state)
        }

        if (state == "PROCESSING" && fileName.isNotBlank()) {
            waitForGeminiFileActive(fileName, apiKey)
        }

        return UploadedGeminiFile(fileName, fileUri)
    }

    private fun waitForGeminiFileActive(fileName: String, apiKey: String) {
        repeat(15) {
            Thread.sleep(1000L)
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName?key=$apiKey")
                .get()
                .build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string() ?: return
                val state = json.parseToJsonElement(body).jsonObject["state"]?.jsonPrimitive?.content
                if (state == "ACTIVE") return
                if (state == "FAILED") {
                    throw IllegalStateException("Gemini failed to process the uploaded audio file.")
                }
            }
        }
    }

    private fun deleteGeminiFile(fileName: String, apiKey: String) {
        try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName?key=$apiKey")
                .delete()
                .build()
            httpClient.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(tag, "Failed to clean up Gemini uploaded file $fileName", e)
        }
    }

    companion object {
        private const val GEMINI_INLINE_THRESHOLD_BYTES = 4L * 1024L * 1024L
    }

    private fun transcribeWithDeepgram(
        audioFile: File,
        model: String,
        apiKey: String,
        language: String,
        diarize: Boolean
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Error("Deepgram API Key is not configured. Please add it in Settings.")
        }

        val chosenModel = if (model.isBlank()) "nova-2" else model
        val langParam = if (language.isNotBlank()) "&language=${language.trim()}" else ""
        val diarizeParam = if (diarize) "&diarize=true&paragraphs=true" else ""
        val url = "https://api.deepgram.com/v1/listen?model=$chosenModel&smart_format=true$diarizeParam$langParam"

        try {
            val requestBody = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Token ${apiKey.trim()}")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val parsed = json.parseToJsonElement(body).jsonObject
                        parsed["err_msg"]?.jsonPrimitive?.content ?: parsed["message"]?.jsonPrimitive?.content ?: body
                    } catch (e: Exception) {
                        body
                    }
                    return TranscriptionResult.Error("Deepgram Error (${response.code}): $errMsg")
                }

                val parsed = json.parseToJsonElement(body).jsonObject
                val results = parsed["results"]?.jsonObject
                val channels = results?.get("channels")?.jsonArray
                val firstChannel = channels?.getOrNull(0)?.jsonObject
                val alternatives = firstChannel?.get("alternatives")?.jsonArray
                val firstAlt = alternatives?.getOrNull(0)?.jsonObject

                // Try to parse structured paragraphs with speaker labels
                val paragraphsObj = firstAlt?.get("paragraphs")?.jsonObject
                val paragraphsArray = paragraphsObj?.get("paragraphs")?.jsonArray

                val transcriptText = if (diarize && paragraphsArray != null && paragraphsArray.size > 0) {
                    val sb = StringBuilder()
                    for (p in paragraphsArray) {
                        val pObj = p.jsonObject
                        val speaker = pObj["speaker"]?.jsonPrimitive?.content ?: "0"
                        val sentences = pObj["sentences"]?.jsonArray
                        val pText = sentences?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }?.joinToString(" ")
                            ?: ""
                        if (pText.isNotBlank()) {
                            sb.append("Speaker $speaker: $pText\n\n")
                        }
                    }
                    sb.toString().trim()
                } else {
                    firstAlt?.get("transcript")?.jsonPrimitive?.content ?: ""
                }

                if (transcriptText.isBlank()) {
                    return TranscriptionResult.Error("Deepgram returned an empty transcript.")
                }

                return TranscriptionResult.Success(transcriptText.trim(), "Deepgram ($chosenModel)")
            }
        } catch (e: Exception) {
            Log.e(tag, "Deepgram transcription failed", e)
            return TranscriptionResult.Error("Deepgram connection error: ${e.localizedMessage ?: "Failed to connect"}")
        }
    }

    private fun transcribeWithCustom(
        audioFile: File,
        baseUrl: String,
        model: String,
        apiKey: String,
        language: String,
        prompt: String
    ): TranscriptionResult {
        if (baseUrl.isBlank()) {
            return TranscriptionResult.Error("Custom endpoint URL is not configured.")
        }

        val cleanUrl = if (baseUrl.endsWith("/audio/transcriptions")) {
            baseUrl
        } else if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")) {
            baseUrl.trimEnd('/') + "/audio/transcriptions"
        } else {
            baseUrl.trimEnd('/') + "/v1/audio/transcriptions"
        }

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            .addFormDataPart("model", model.ifBlank { "whisper-1" })
            .addFormDataPart("response_format", "json")

        if (language.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("language", language.trim())
        }
        if (prompt.isNotBlank()) {
            requestBodyBuilder.addFormDataPart("prompt", prompt.trim())
        }

        val requestBuilder = Request.Builder()
            .url(cleanUrl)
            .post(requestBodyBuilder.build())

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        return executeWhisperRequest(requestBuilder.build(), "Custom Server")
    }

    private fun executeWhisperRequest(request: Request, providerName: String): TranscriptionResult {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val parsed = json.parseToJsonElement(body).jsonObject
                        parsed["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: body
                    } catch (e: Exception) {
                        body
                    }
                    return TranscriptionResult.Error("$providerName API Error (${response.code}): $errMsg")
                }

                val parsed = json.parseToJsonElement(body).jsonObject
                val text = parsed["text"]?.jsonPrimitive?.content
                    ?: parsed["transcript"]?.jsonPrimitive?.content
                    ?: body

                if (text.isBlank()) {
                    return TranscriptionResult.Error("$providerName returned an empty transcript.")
                }

                TranscriptionResult.Success(text.trim(), providerName)
            }
        } catch (e: Exception) {
            Log.e(tag, "$providerName Whisper request failed", e)
            TranscriptionResult.Error("Network error: ${e.localizedMessage ?: "Failed to connect"}")
        }
    }

    suspend fun testProviderConnection(provider: AiProvider, settings: AppSettings): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                when (provider) {
                    AiProvider.OPENAI -> {
                        if (settings.openaiApiKey.isBlank()) return@withContext Result.failure(Exception("OpenAI API Key is empty"))
                        val req = Request.Builder()
                            .url("https://api.openai.com/v1/models")
                            .header("Authorization", "Bearer ${settings.openaiApiKey.trim()}")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) Result.success("OpenAI connection successful!")
                            else Result.failure(Exception("OpenAI Error (${res.code}): ${res.body?.string() ?: ""}"))
                        }
                    }
                    AiProvider.GROQ -> {
                        if (settings.groqApiKey.isBlank()) return@withContext Result.failure(Exception("Groq API Key is empty"))
                        val req = Request.Builder()
                            .url("https://api.groq.com/openai/v1/models")
                            .header("Authorization", "Bearer ${settings.groqApiKey.trim()}")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) Result.success("Groq connection successful!")
                            else Result.failure(Exception("Groq Error (${res.code}): ${res.body?.string() ?: ""}"))
                        }
                    }
                    AiProvider.GEMINI -> {
                        if (settings.geminiApiKey.isBlank()) return@withContext Result.failure(Exception("Gemini API Key is empty"))
                        val req = Request.Builder()
                            .url("https://generativelanguage.googleapis.com/v1beta/models?key=${settings.geminiApiKey.trim()}")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) Result.success("Gemini connection successful!")
                            else Result.failure(Exception("Gemini Error (${res.code}): ${res.body?.string() ?: ""}"))
                        }
                    }
                    AiProvider.DEEPGRAM -> {
                        if (settings.deepgramApiKey.isBlank()) return@withContext Result.failure(Exception("Deepgram API Key is empty"))
                        val req = Request.Builder()
                            .url("https://api.deepgram.com/v1/projects")
                            .header("Authorization", "Token ${settings.deepgramApiKey.trim()}")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) Result.success("Deepgram connection successful!")
                            else Result.failure(Exception("Deepgram Error (${res.code}): ${res.body?.string() ?: ""}"))
                        }
                    }
                    AiProvider.CUSTOM -> {
                        if (settings.customBaseUrl.isBlank()) return@withContext Result.failure(Exception("Custom Base URL is empty"))
                        val target = settings.customBaseUrl.trimEnd('/') + "/models"
                        val reqBuilder = Request.Builder().url(target).get()
                        if (settings.customApiKey.isNotBlank()) {
                            reqBuilder.header("Authorization", "Bearer ${settings.customApiKey.trim()}")
                        }
                        httpClient.newCall(reqBuilder.build()).execute().use { res ->
                            if (res.isSuccessful) Result.success("Custom endpoint reached successfully!")
                            else Result.failure(Exception("Custom Server responded (${res.code})"))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}