package com.example.voicescribe.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context) {

    private val tag = "AudioRecorderManager"
    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set

    var isRecording = false
        private set
    var isPaused = false
        private set

    fun startRecording(targetFile: File): Boolean {
        return try {
            stopRecording()

            outputFile = targetFile
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(targetFile.absolutePath)
                prepare()
                start()
            }

            recorder = mr
            isRecording = true
            isPaused = false
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start recording", e)
            recorder?.release()
            recorder = null
            isRecording = false
            false
        }
    }

    fun pauseRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            return try {
                recorder?.pause()
                isPaused = true
                true
            } catch (e: Exception) {
                Log.e(tag, "Failed to pause recording", e)
                false
            }
        }
        return false
    }

    fun resumeRecording(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            return try {
                recorder?.resume()
                isPaused = false
                true
            } catch (e: Exception) {
                Log.e(tag, "Failed to resume recording", e)
                false
            }
        }
        return false
    }

    fun stopRecording(): Boolean {
        if (!isRecording && recorder == null) return true
        return try {
            recorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w(tag, "Exception during stop", e)
                }
                reset()
                release()
            }
            recorder = null
            isRecording = false
            isPaused = false
            true
        } catch (e: Exception) {
            Log.e(tag, "Error stopping recorder", e)
            recorder = null
            isRecording = false
            isPaused = false
            false
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            if (isRecording && !isPaused) {
                recorder?.maxAmplitude ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
}