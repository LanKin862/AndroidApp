package com.example.myapp.ui.music

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Path

private const val TAG_SPECTRUM = "SpectrumAnalyzer"

@Composable
fun SpectrumAnalyzer(
    fftData: ByteArray?,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 32,
    minBarHeight: Dp = 2.dp,
    maxBarHeightScale: Float = 0.9f,
    smoothingFactor: Float = 0.3f,  // 增加平滑因子使过渡更流畅
    dynamicResponseSpeed: Float = 0.5f,  // 新增动态响应速度参数
    isPlaying: Boolean = true  // 添加播放状态参数，默认值为true
) {
    val previousMagnitudes = remember { mutableStateOf(FloatArray(barCount)) }
    val peakMagnitudes = remember { mutableStateOf(FloatArray(barCount)) }
    val decayRates = remember { mutableStateOf(FloatArray(barCount)) }

    // 当不播放时清除幅度值的效果
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            // 暂停时清除先前的幅度和峰值
            previousMagnitudes.value = FloatArray(barCount)
            peakMagnitudes.value = FloatArray(barCount)
        }
    }

    //记住高频的独立随机因子
    val highFreqRandomFactors = remember { 
        mutableStateOf(FloatArray(barCount) { 0.5f + (Math.random() * 0.5f).toFloat() })
    }

    // 生成频谱颜色
    val colors = remember {
        val baseColor = barColor
        List(barCount) { index ->
            val fraction = index.toFloat() / barCount
            when {
                fraction < 0.15f -> Color(
                    red = baseColor.red * 0.9f,
                    green = baseColor.green * 0.5f,
                    blue = baseColor.blue * 0.9f,
                    alpha = baseColor.alpha
                )
                fraction < 0.3f -> Color(
                    red = baseColor.red,
                    green = baseColor.green * 0.6f,
                    blue = baseColor.blue * 0.8f,
                    alpha = baseColor.alpha
                )
                fraction < 0.45f -> Color(
                    red = baseColor.red * 0.8f,
                    green = baseColor.green * 0.8f,
                    blue = baseColor.blue,
                    alpha = baseColor.alpha
                )
                fraction < 0.6f -> Color(
                    red = baseColor.red * 0.7f,
                    green = baseColor.green,
                    blue = baseColor.blue * 0.9f,
                    alpha = baseColor.alpha
                )
                fraction < 0.75f -> Color(
                    red = baseColor.red * 0.8f,
                    green = baseColor.green * 0.9f,
                    blue = baseColor.blue * 0.7f,
                    alpha = baseColor.alpha
                )
                fraction < 0.9f -> Color(
                    red = baseColor.red * 0.9f,
                    green = baseColor.green * 0.8f,
                    blue = baseColor.blue * 0.6f,
                    alpha = baseColor.alpha
                )
                else -> Color(
                    red = baseColor.red,
                    green = baseColor.green * 0.7f,
                    blue = baseColor.blue * 0.8f,
                    alpha = baseColor.alpha
                )
            }
        }
    }

    Canvas(modifier = modifier) {
        // 如果没有FFT数据，直接返回不绘制任何内容
        if (fftData == null || fftData.isEmpty() || !isPlaying) {
            return@Canvas
        }

        val canvasWidth = size.width
        val canvasHeight = size.height

        // 调整柱状图间距
        val barSpacingPercent = if (canvasWidth < 200.dp.toPx()) 0.05f else 0.08f
        val barWidthWithSpacing = canvasWidth / barCount
        val spacing = barWidthWithSpacing * barSpacingPercent
        val barActualWidth = barWidthWithSpacing - spacing

        val newMagnitudes = FloatArray(barCount)

        // 处理FFT数据
        for (i in 0 until barCount) {
            val startIndex = (i.toFloat() * fftData.size / barCount).toInt()
            val endIndex = ((i + 1).toFloat() * fftData.size / barCount).toInt().coerceAtMost(fftData.size)

            var sum = 0f
            var count = 0

            for (j in startIndex until endIndex) {
                val value = fftData[j].toInt() and 0xFF
                sum += value / 255f
                count++
            }

            //压缩动态范围频率加权曲线
            val frequencyWeightingCurve = when {
                i < barCount * 0.1f -> 0.9f + (i / (barCount * 0.1f)) * 0.5f  // 超低音（从0.7f增加）
                i < barCount * 0.25f -> 1.4f  // 低音（从1.1f增加）
                i < barCount * 0.4f -> 1.3f   // 低中音（从1.0f增加）
                i < barCount * 0.6f -> 1.2f   // 中音（从0.9f增加）
                i < barCount * 0.75f -> 1.1f  // 中高音（从0.8f增加）
                i < barCount * 0.9f -> 1.0f   // 高音（从0.7f增加）
                else -> 0.9f                  // 超高音（从0.6f增加）
            }

            //对高频应用独立随机因子
            val randomFactor = if (i >= barCount * 0.6f) {
                //偶尔更新高频的随机因子
                if (Math.random() < 0.05) {  // 5%的概率更新
                    highFreqRandomFactors.value[i] = 0.3f + (Math.random() * 0.7f).toFloat()
                }
                highFreqRandomFactors.value[i]
            } else {
                1.0f  //对于低频/中频没有随机因子
            }

            //应用对数压缩以减小动态范围
            val rawValue = if (count > 0) (sum / count) else 0f
            val compressedValue = if (rawValue > 0) {
                val logBase = 10f
                val compressionFactor = 0.45f  // 从0.5f降低，以保留更多动态范围
                (1f + compressionFactor * (Math.log10((1f + rawValue * (logBase - 1f)).toDouble()) / Math.log10(logBase.toDouble()))).toFloat() - 1f
            } else {
                0f
            }

            newMagnitudes[i] = compressedValue * frequencyWeightingCurve * randomFactor

            // 更新峰值和衰减率
            if (newMagnitudes[i] > peakMagnitudes.value[i]) {
                peakMagnitudes.value[i] = newMagnitudes[i]
                //更快的高频衰减
                decayRates.value[i] = if (i >= barCount * 0.6f) {
                    0.08f + newMagnitudes[i] * 0.25f
                } else {
                    0.05f + newMagnitudes[i] * 0.2f
                }
            } else {
                peakMagnitudes.value[i] = (peakMagnitudes.value[i] - decayRates.value[i]).coerceAtLeast(0f)
            }
        }

        // 应用平滑处理 - 高频使用较低的平滑因子
        val smoothedMagnitudes = FloatArray(barCount) { i ->
            val adjustedSmoothingFactor = if (i >= barCount * 0.6f) {
                //对高频平滑较少（变化更快）
                smoothingFactor * 1.5f
            } else {
                smoothingFactor
            }
            
            previousMagnitudes.value[i] * (1 - adjustedSmoothingFactor) +
                    newMagnitudes[i] * adjustedSmoothingFactor * dynamicResponseSpeed
        }

        previousMagnitudes.value = smoothedMagnitudes

        // 绘制柱状图
        for (i in 0 until barCount) {
            // 使用峰值和当前值的混合来创建更生动的效果
            val peakInfluence = if (i >= barCount * 0.6f) 0.2f else 0.3f  //对高频的峰值影响较小
            val dynamicMagnitude = (smoothedMagnitudes[i] * (1f - peakInfluence) + peakMagnitudes.value[i] * peakInfluence)
                .coerceIn(0f, 1f)

            val barHeight = (dynamicMagnitude * canvasHeight * maxBarHeightScale)
                .coerceAtLeast(minBarHeight.toPx())

            val x = i * barWidthWithSpacing + spacing / 2
            val y = canvasHeight - barHeight

            // 根据幅度和位置创建颜色
            val barColorWithAlpha = colors[i].copy(
                alpha = 0.4f + dynamicMagnitude * 0.6f
            )

            // 绘制带圆角顶部的柱状图
            if (barActualWidth > 3f) {
                val cornerRadius = (barActualWidth * 0.5f).coerceAtMost(4f)
                val path = Path().apply {
                    moveTo(x, y + cornerRadius)
                    quadraticBezierTo(x, y, x + cornerRadius, y)
                    lineTo(x + barActualWidth - cornerRadius, y)
                    quadraticBezierTo(x + barActualWidth, y, x + barActualWidth, y + cornerRadius)
                    lineTo(x + barActualWidth, canvasHeight)
                    lineTo(x, canvasHeight)
                    lineTo(x, y + cornerRadius)
                    close()
                }

                drawPath(
                    path = path,
                    color = barColorWithAlpha,
                    style = Fill
                )
            } else {
                drawRect(
                    color = barColorWithAlpha,
                    topLeft = Offset(x = x, y = y),
                    size = Size(width = barActualWidth, height = barHeight)
                )
            }
        }
    }
}