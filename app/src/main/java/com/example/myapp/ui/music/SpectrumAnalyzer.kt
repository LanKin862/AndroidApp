package com.example.myapp.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
fun SpectrumAnalyzer(
    fftData: ByteArray?,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 32, // Default to 32 bars
    minBarHeight: Dp = 1.dp,
    maxBarHeightScale: Float = 1.0f, // Scale factor for max bar height
    smoothingFactor: Float = 0.15f // Smoothing factor (0.0 to 1.0)
) {
    val previousMagnitudes = remember { mutableStateOf(FloatArray(barCount)) }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        val barWidthWithSpacing = canvasWidth / barCount
        val spacing = (barWidthWithSpacing * 0.2f).coerceAtMost(barWidthWithSpacing * 0.5f)
        val barActualWidth = barWidthWithSpacing - spacing

        // Check if fftData is valid for processing
        if (fftData == null || fftData.size < barCount * 2) { // Minimum data needed for pairs
            for (i in 0 until barCount) {
                val x = i * barWidthWithSpacing + spacing / 2
                drawRect(
                    color = barColor.copy(alpha = 0.3f),
                    topLeft = Offset(x = x, y = canvasHeight - minBarHeight.toPx()),
                    size = Size(width = barActualWidth, height = minBarHeight.toPx())
                )
            }
            // Clear previous magnitudes if data is invalid to avoid stale display on next valid data
            if (fftData == null) previousMagnitudes.value = FloatArray(barCount)
            return@Canvas
        }

        val newMagnitudes = FloatArray(barCount)
        
        // Number of complex FFT samples available from the Visualizer output.
        // Each complex sample consists of a real and an imaginary part.
        val numComplexFftSamples = fftData.size / 2

        for (i in 0 until barCount) {
            // Determine the range of complex FFT samples to average for this bar.
            // Ensure that float division is used for calculating indices to distribute samples evenly.
            val fftSampleStartIndex = (i.toFloat() * numComplexFftSamples / barCount).toInt()
            val fftSampleEndIndex = ((i + 1).toFloat() * numComplexFftSamples / barCount).toInt().coerceAtMost(numComplexFftSamples)
            
            var accumulatedMagnitude = 0f
            var samplesCounted = 0

            // Iterate over the designated range of complex FFT samples.
            for (sampleIndex in fftSampleStartIndex until fftSampleEndIndex) {
                val realPartIndex = sampleIndex * 2
                val imagPartIndex = sampleIndex * 2 + 1

                // Ensure indices are within the bounds of the fftData array.
                if (imagPartIndex < fftData.size) {
                    val real = fftData[realPartIndex].toFloat()
                    val imag = fftData[imagPartIndex].toFloat()
                    accumulatedMagnitude += hypot(real, imag)
                    samplesCounted++
                }
            }
            
            val averageMagnitude = if (samplesCounted > 0) accumulatedMagnitude / samplesCounted else 0f
            
            // Normalize magnitude. The divisor (e.g., 256f) is empirical and may need adjustment
            // based on the actual range of values from `hypot(real, imag)`.
            // Max hypot for byte values (127, 127) is approx 180.
            // If Visualizer captureSize is larger, this max magnitude increases.
            // A typical FFT output value might be scaled by captureSize.
            // For now, 256f is a placeholder.
            val normalizedMagnitude = (averageMagnitude / 180f).coerceIn(0f, 1f) 
            newMagnitudes[i] = normalizedMagnitude
        }

        // Apply smoothing: current = previous * (1-alpha) + new * alpha
        val smoothedMagnitudes = FloatArray(barCount) { i ->
            previousMagnitudes.value[i] * (1 - smoothingFactor) + newMagnitudes[i] * smoothingFactor
        }
        previousMagnitudes.value = smoothedMagnitudes // Update for the next frame

        // Draw the smoothed spectrum bars
        smoothedMagnitudes.forEachIndexed { index, magnitude ->
            val barHeightPx = (magnitude * canvasHeight * maxBarHeightScale).coerceAtLeast(minBarHeight.toPx())
            val x = index * barWidthWithSpacing + spacing / 2 // Position includes spacing offset
            
            drawRect(
                color = barColor,
                topLeft = Offset(x = x, y = canvasHeight - barHeightPx),
                size = Size(width = barActualWidth, height = barHeightPx)
            )
        }
    }
}
