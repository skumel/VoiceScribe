package com.example.voicescribe.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.voicescribe.model.RecordingItem
import com.example.voicescribe.model.RecordingStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val recordings by viewModel.filteredRecordings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedItemForDetail by viewModel.selectedItemForDetail.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val isTranscribingMap by viewModel.isTranscribingMap.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History & Files", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.scanForRecoveredRecordings() }) {
                        Icon(Icons.Default.Restore, contentDescription = "Scan for lost recordings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search title or transcript...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == HistoryFilter.ALL,
                    onClick = { viewModel.onFilterSelected(HistoryFilter.ALL) },
                    label = { Text("All (${recordings.size})") }
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.TRANSCRIBED,
                    onClick = { viewModel.onFilterSelected(HistoryFilter.TRANSCRIBED) },
                    label = { Text("Transcribed") }
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.AUDIO_ONLY,
                    onClick = { viewModel.onFilterSelected(HistoryFilter.AUDIO_ONLY) },
                    label = { Text("Audio Saved") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List of Recordings
            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No recordings yet" else "No matching recordings found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recorded audio and transcriptions will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp)
                ) {
                    items(recordings, key = { it.id }) { item ->
                        val isItemTranscribing = isTranscribingMap[item.id] == true
                        RecordingCard(
                            item = item,
                            isTranscribing = isItemTranscribing,
                            onClick = { viewModel.selectItemForDetail(item) }
                        )
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet / Dialog
    selectedItemForDetail?.let { item ->
        RecordingDetailSheet(
            item = item,
            playerState = playerState,
            isTranscribing = isTranscribingMap[item.id] == true,
            onPlayAudio = { path -> viewModel.playAudio(path) },
            onPauseAudio = { viewModel.pauseAudio() },
            onSeekAudio = { pos -> viewModel.seekAudio(pos) },
            onTranscribe = { itm, prov, mod -> viewModel.transcribeRecording(itm, prov, mod) },
            onDeleteAudioOnly = { id -> viewModel.deleteAudioOnly(id) },
            onDeleteRecording = { id -> viewModel.deleteRecording(id) },
            onDismiss = { viewModel.selectItemForDetail(null) }
        )
    }
}

@Composable
private fun RecordingCard(
    item: RecordingItem,
    isTranscribing: Boolean,
    onClick: () -> Unit
) {
    val formattedDate = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = formatDuration(item.durationMs),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Transcript Excerpt or Status
            if (!item.transcript.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.transcript,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (isTranscribing) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Transcribing with AI...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            } else if (item.status == RecordingStatus.ERROR) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Transcription failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.modelUsed != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = item.modelUsed.split(" ")[0],
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (item.isRecovered) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Recovered",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Text(
                    text = if (item.isAudioDeleted) "Audio Deleted" else "Audio Kept",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isAudioDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSecs = millis / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}