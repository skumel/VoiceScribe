package com.example.voicescribe.ui.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 36,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val barAmplitudes = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.08f) } } }

    LaunchedEffect(amplitude, isRecording, isPaused) {
        if (isRecording && !isPaused) {
            val smoothed = amplitude.coerceIn(0.05f, 1.0f)
            if (barAmplitudes.size >= barCount) {
                barAmplitudes.removeAt(0)
            }
            // Add a little organic variance for visual aesthetics
            val variance = (Random.nextFloat() * 0.2f) - 0.1f
            barAmplitudes.add((smoothed + variance).coerceIn(0.06f, 1.0f))
        } else if (isPaused) {
            // Keep current bars static
        } else {
            // Idle flat state
            for (i in 0 until barAmplitudes.size) {
                barAmplitudes[i] = 0.06f
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val totalSpacing = canvasWidth * 0.3f
        val barWidth = (canvasWidth - totalSpacing) / barCount
        val spacing = totalSpacing / (barCount - 1).coerceAtLeast(1)

        val brush = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor)
        )

        for (i in 0 until barAmplitudes.size) {
            val amp = barAmplitudes[i]
            val barHeight = (canvasHeight * amp).coerceAtLeast(6.dp.toPx())
            val x = i * (barWidth + spacing)
            val y = (canvasHeight - barHeight) / 2f

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}