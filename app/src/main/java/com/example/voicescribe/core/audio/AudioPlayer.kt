package com.example.voicescribe.core.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val filePath: String? = null
)

class AudioPlayer(private val scope: CoroutineScope) {

    private val tag = "AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun play(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(tag, "Audio file does not exist: $filePath")
            return
        }

        if (_playerState.value.filePath == filePath && mediaPlayer != null) {
            mediaPlayer?.start()
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startProgressTracking()
            return
        }

        stop()

        try {
            val mp = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(isPlaying = false, currentPositionMs = 0)
                    progressJob?.cancel()
                }
                start()
            }
            mediaPlayer = mp
            _playerState.value = PlayerState(
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = mp.duration.toLong(),
                filePath = filePath
            )
            startProgressTracking()
        } catch (e: Exception) {
            Log.e(tag, "Error playing audio file", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            progressJob?.cancel()
        } catch (e: Exception) {
            Log.e(tag, "Error pausing audio player", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
        } catch (e: Exception) {
            Log.e(tag, "Error seeking audio player", e)
        }
    }

    fun stop() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing media player", e)
        } finally {
            mediaPlayer = null
            _playerState.value = PlayerState()
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(100)
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = mp.currentPosition.toLong(),
                            durationMs = mp.duration.toLong()
                        )
                    }
                }
            }
        }
    }
}