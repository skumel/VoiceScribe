package com.example.voicescribe.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.AppThemePalette
import com.example.voicescribe.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()
    val testState by viewModel.testState.collectAsState()

    var openaiKeyInput by remember(settings.openaiApiKey) { mutableStateOf(settings.openaiApiKey) }
    var groqKeyInput by remember(settings.groqApiKey) { mutableStateOf(settings.groqApiKey) }
    var geminiKeyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var deepgramKeyInput by remember(settings.deepgramApiKey) { mutableStateOf(settings.deepgramApiKey) }
    var customUrlInput by remember(settings.customBaseUrl) { mutableStateOf(settings.customBaseUrl) }
    var customKeyInput by remember(settings.customApiKey) { mutableStateOf(settings.customApiKey) }
    var customModelInput by remember(settings.customModelName) { mutableStateOf(settings.customModelName) }

    var isKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // General / Recording Behavior
            SectionHeader("Recording & Transcription")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-record on Launch", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Instantly start recording when the app opens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoRecordOnLaunch,
                            onCheckedChange = { viewModel.setAutoRecord(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Separate Speakers (Diarization)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Distinguish persons speaking and label Speaker 1, Speaker 2 in transcripts (Gemini & Deepgram)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.speakerDiarizationEnabled,
                            onCheckedChange = { viewModel.setSpeakerDiarization(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-delete Audio after Transcription", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Deletes original audio file once transcribed to save device storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoDeleteAudioAfterTranscription,
                            onCheckedChange = { viewModel.setAutoDeleteAudio(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AI Model Configuration
            SectionHeader("AI Transcription Provider")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Default Provider:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))

                    AiProvider.values().forEach { provider ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setProvider(provider) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = settings.selectedProvider == provider,
                                onClick = { viewModel.setProvider(provider) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                Text("Model: ${provider.defaultModel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // API Keys per provider
                    when (settings.selectedProvider) {
                        AiProvider.OPENAI -> {
                            ApiKeyField(
                                label = "OpenAI API Key",
                                value = openaiKeyInput,
                                onValueChange = {
                                    openaiKeyInput = it
                                    viewModel.setOpenAiKey(it)
                                },
                                isVisible = isKeyVisible,
                                onToggleVisibility = { isKeyVisible = !isKeyVisible },
                                placeholder = "sk-..."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.testConnection(AiProvider.OPENAI) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test OpenAI Connection")
                            }
                        }
                        AiProvider.GROQ -> {
                            ApiKeyField(
                                label = "Groq API Key (Ultra Fast Whisper)",
                                value = groqKeyInput,
                                onValueChange = {
                                    groqKeyInput = it
                                    viewModel.setGroqKey(it)
                                },
                                isVisible = isKeyVisible,
                                onToggleVisibility = { isKeyVisible = !isKeyVisible },
                                placeholder = "gsk_..."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.testConnection(AiProvider.GROQ) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Groq Connection")
                            }
                        }
                        AiProvider.GEMINI -> {
                            ApiKeyField(
                                label = "Google Gemini API Key",
                                value = geminiKeyInput,
                                onValueChange = {
                                    geminiKeyInput = it
                                    viewModel.setGeminiKey(it)
                                },
                                isVisible = isKeyVisible,
                                onToggleVisibility = { isKeyVisible = !isKeyVisible },
                                placeholder = "AIzaSy..."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.testConnection(AiProvider.GEMINI) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Gemini Connection")
                            }
                        }
                        AiProvider.DEEPGRAM -> {
                            ApiKeyField(
                                label = "Deepgram API Key (Native Diarization)",
                                value = deepgramKeyInput,
                                onValueChange = {
                                    deepgramKeyInput = it
                                    viewModel.setDeepgramKey(it)
                                },
                                isVisible = isKeyVisible,
                                onToggleVisibility = { isKeyVisible = !isKeyVisible },
                                placeholder = "Token..."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.testConnection(AiProvider.DEEPGRAM) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Deepgram Connection")
                            }
                        }
                        AiProvider.CUSTOM -> {
                            OutlinedTextField(
                                value = customUrlInput,
                                onValueChange = {
                                    customUrlInput = it
                                    viewModel.setCustomConfig(it, customKeyInput, customModelInput)
                                },
                                label = { Text("Base URL") },
                                placeholder = { Text("http://192.168.1.100:8000/v1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ApiKeyField(
                                label = "API Key (Optional)",
                                value = customKeyInput,
                                onValueChange = {
                                    customKeyInput = it
                                    viewModel.setCustomConfig(customUrlInput, it, customModelInput)
                                },
                                isVisible = isKeyVisible,
                                onToggleVisibility = { isKeyVisible = !isKeyVisible },
                                placeholder = "Bearer token..."
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customModelInput,
                                onValueChange = {
                                    customModelInput = it
                                    viewModel.setCustomConfig(customUrlInput, customKeyInput, it)
                                },
                                label = { Text("Model Name") },
                                placeholder = { Text("whisper-1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.testConnection(AiProvider.CUSTOM) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Custom Server")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Appearance & Color Themes
            SectionHeader("Appearance & Color Themes")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme Mode", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Color Palette", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemePalette.values().forEach { palette ->
                            val isSelected = settings.themePalette == palette
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setThemePalette(palette) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(palette.primaryHex))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = palette.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Connection Test Dialog
    if (testState !is ConnectionTestState.Idle) {
        Dialog(onDismissRequest = {
            if (testState !is ConnectionTestState.Testing) viewModel.dismissTestDialog()
        }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val state = testState) {
                        is ConnectionTestState.Testing -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Testing API Connection...", fontWeight = FontWeight.SemiBold)
                        }
                        is ConnectionTestState.Success -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Connection Successful!", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.dismissTestDialog() }, modifier = Modifier.fillMaxWidth()) {
                                Text("OK")
                            }
                        }
                        is ConnectionTestState.Error -> {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Connection Failed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.dismissTestDialog() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Dismiss")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Hide Key" else "Show Key"
                )
            }
        }
    )
}