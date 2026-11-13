package com.example.voicescribe.ui.history

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.voicescribe.core.audio.PlayerState
import com.example.voicescribe.model.AiProvider
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailSheet(
    item: RecordingItem,
    playerState: PlayerState,
    isTranscribing: Boolean,
    onPlayAudio: (String) -> Unit,
    onPauseAudio: () -> Unit,
    onSeekAudio: (Long) -> Unit,
    onTranscribe: (RecordingItem, AiProvider?, String?) -> Unit,
    onDeleteAudioOnly: (String) -> Unit,
    onDeleteRecording: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showTranscribeOptions by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(AiProvider.GROQ) }

    val formattedDate = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    val isThisPlaying = playerState.isPlaying && playerState.filePath == item.audioFilePath

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$formattedDate · ${formatDuration(item.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.modelUsed != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = item.modelUsed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.isAudioDeleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (item.isAudioDeleted) "Audio Deleted (Saved Storage)" else "Audio Available (${formatFileSize(item.fileSizeBytes)})",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isAudioDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable Content Area: Audio Player (if present) + Transcript Text
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Audio Player Component
                    if (!item.isAudioDeleted && item.audioFilePath != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (isThisPlaying) onPauseAudio() else onPlayAudio(item.audioFilePath)
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isThisPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        val currentPos = if (isThisPlaying) playerState.currentPositionMs else 0L
                                        val totalDur = if (isThisPlaying && playerState.durationMs > 0) playerState.durationMs else item.durationMs

                                        Slider(
                                            value = if (totalDur > 0) currentPos.toFloat() / totalDur.toFloat() else 0f,
                                            onValueChange = { frac ->
                                                if (totalDur > 0) {
                                                    onSeekAudio((frac * totalDur).toLong())
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(formatDuration(currentPos), style = MaterialTheme.typography.labelSmall)
                                            Text(formatDuration(totalDur), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                // Option to delete audio only to save space
                                if (!item.transcript.isNullOrBlank()) {
                                    TextButton(
                                        onClick = { onDeleteAudioOnly(item.id) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete Audio Only (Keep Text)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // Transcript Text Box
                    Text(
                        text = "TRANSCRIPT",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!item.transcript.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainerWrapper {
                                Text(
                                    text = item.transcript,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else if (item.status == RecordingStatus.ERROR) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Transcription Error",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    item.errorMessage ?: "Unknown error occurred",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No transcript available yet. Tap Transcribe below to generate one with AI.",
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Re-transcribe model choice
                    if (showTranscribeOptions && !item.isAudioDeleted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Choose Model:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(6.dp))
                                AiProvider.values().forEach { prov ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = selectedProvider == prov,
                                            onClick = { selectedProvider = prov }
                                        )
                                        Text(prov.displayName, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Button(
                                    onClick = {
                                        showTranscribeOptions = false
                                        onTranscribe(item, selectedProvider, selectedProvider.defaultModel)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Start Transcription")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Bottom Action Bar: Copy, Share, Transcribe, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.transcript.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(item.transcript))
                                Toast.makeText(context, "Transcript copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }

                        OutlinedButton(
                            onClick = {
                                shareTranscript(context, item.title, item.transcript)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }

                    if (!item.isAudioDeleted && item.audioFilePath != null) {
                        Button(
                            onClick = {
                                if (!showTranscribeOptions) {
                                    showTranscribeOptions = true
                                } else {
                                    onTranscribe(item, selectedProvider, selectedProvider.defaultModel)
                                    showTranscribeOptions = false
                                }
                            },
                            enabled = !isTranscribing,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            if (isTranscribing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (item.transcript.isNullOrBlank()) "Transcribe" else "Re-Transcribe")
                            }
                        }
                    }

                    IconButton(
                        onClick = { onDeleteRecording(item.id) },
                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionContainerWrapper(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}

private fun shareTranscript(context: Context, title: String, transcript: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, transcript)
    }
    context.startActivity(Intent.createChooser(intent, "Share Transcript"))
}

private fun formatDuration(millis: Long): String {
    val totalSecs = millis / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(Locale.getDefault(), "%.1f MB", mb)
    else String.format(Locale.getDefault(), "%.0f KB", kb)
}