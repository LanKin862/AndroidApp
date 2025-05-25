package com.example.myapp.ui.music

import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "SpectrogramView"

@Composable
fun SpectrogramView(
    fftData: ByteArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    mode: VisualizationMode = VisualizationMode.BARS
) {
    // Track if we've ever received valid data
    var hasReceivedData by remember { mutableStateOf(false) }
    
    // Debug logging for FFT data
    LaunchedEffect(fftData) {
        if (fftData.isNotEmpty()) {
            Log.d(TAG, "SpectrogramView received FFT data: length=${fftData.size}")
            hasReceivedData = true
        }
    }
    
    // 保存最后收到的有效FFT数据
    var lastValidFftData by remember { mutableStateOf(ByteArray(0)) }
    
    // 记住上一次的可视化模式，用于动画
    var previousMode by remember { mutableStateOf(mode) }
    
    // 动画进度 - 从0到1的过渡
    val animationProgress by animateFloatAsState(
        targetValue = if (previousMode == mode) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        finishedListener = {
            if (it == 0f) {
                previousMode = mode
            }
        }
    )
    
    // 切换模式后，设置为0以开始动画
    if (previousMode != mode) {
        LaunchedEffect(mode) {
            previousMode = mode
        }
    }
    
    // 如果收到新的有效数据，更新lastValidFftData
    if (fftData.isNotEmpty()) {
        lastValidFftData = fftData
        if (!hasReceivedData) {
            hasReceivedData = true
        }
    }
    
    // 使用有效的FFT数据（当前数据或最后一帧）
    val displayData = if (fftData.isNotEmpty()) fftData else lastValidFftData
    
    // 获取主题颜色
    val colorScheme = MaterialTheme.colorScheme
    
    // 背景颜色
    val backgroundColor = colorScheme.surface.copy(alpha = 0.9f)
    val surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 0.3f) // 用于可视化背景
    
    // 设置渐变颜色 - 使用Material Design主题色
    val gradientColors = remember(colorScheme) {
        listOf(
            colorScheme.primary.copy(alpha = 0.95f),                     // 起始色
            colorScheme.primary,                                         // 主色
            colorScheme.secondary.copy(alpha = 0.9f),                    // 次色
            colorScheme.tertiary.copy(alpha = 0.95f),                    // 第三色
            lerp(colorScheme.tertiary, colorScheme.error, 0.3f),   // 过渡色1
            lerp(colorScheme.tertiary, colorScheme.error, 0.6f),   // 过渡色2
            colorScheme.error.copy(alpha = 0.9f),                        // 错误色
            colorScheme.errorContainer                                   // 高强度色
        )
    }
    
    // 为频谱可视化定义额外的彩虹渐变色
    val rainbowColors = remember {
        listOf(
            Color(0xFF1A237E).copy(alpha = 0.8f),  // 深靛蓝
            Color(0xFF5C6BC0),                     // 预定义的浅靛蓝
            Color(0xFF64B5F6),                     // 浅蓝色
            Color(0xFF4DD0E1),                     // 浅青色
            Color(0xFF4DB6AC),                     // 浅青绿
            Color(0xFF81C784),                     // 浅绿色
            Color(0xFFAED581),                     // 更浅的酸橙
            Color(0xFFFFD54F),                     // 浅黄色
            Color(0xFFFFB74D),                     // 浅琥珀
            Color(0xFFFF8A65),                     // 浅橙色
            Color(0xFFE57373)                      // 浅红色
        )
    }
    
    // 为波形和圆形可视化定义附加颜色
    val waveGradient = remember(colorScheme) {
        Brush.verticalGradient(
            colors = listOf(
                colorScheme.primaryContainer,
                colorScheme.primary,
                colorScheme.secondary
            )
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor), // 使用主题颜色而不是固定的黑色背景
        contentAlignment = Alignment.Center
    ) {
        if (!hasReceivedData) {
            // 显示等待消息，直到收到第一个FFT数据
            Text(
                "等待音频数据...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (displayData.isEmpty()) {
                // 如果没有数据，只绘制背景
                return@Canvas
            }
            
            when (mode) {
                VisualizationMode.BARS -> drawBars(displayData, isPlaying, rainbowColors)
                VisualizationMode.WAVE -> drawWaveform(displayData, isPlaying, waveGradient, surfaceVariant)
                VisualizationMode.CIRCULAR -> drawCircular(displayData, isPlaying, gradientColors, surfaceVariant)
            }
        }
    }
}
// 绘制条形频谱
private fun DrawScope.drawBars(
    displayData: ByteArray,
    isPlaying: Boolean,
    gradientColors: List<Color>
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    
    // 只使用FFT数据的一部分，优化频率范围的展示
    // 数据是复数对，通常前半部分包含更多的有用频率信息
    val frequencyRange = min(displayData.size / 2, 128) // 使用前128个频率点或更少
    
    // 优化频带展示 - 使用对数频率刻度，更符合人耳的感知
    // 创建非线性的频率索引映射，低频分辨率更高
    val frequencyBands = 64 // 最终绘制的频带数量
    val bandIndices = List(frequencyBands) { i ->
        // 使用指数映射，使低频有更多细节
        val t = i.toFloat() / (frequencyBands - 1)
        val exponentialT = t.pow(2.0f) // 指数参数控制非线性程度
        (exponentialT * (frequencyRange - 1)).toInt()
    }
    
    // 计算每个条形的宽度
    val barWidth = canvasWidth / frequencyBands
    
    // 计算条形的最大高度
    val maxAmplitude = 128f // FFT数据通常是字节范围(-128到127)
    
    // 获取频带的幅值，应用平滑处理
    val bandMagnitudes = bandIndices.mapIndexed { index, freqIndex ->
        // 获取主频率索引处的幅值
        val mainMagnitude = abs(displayData[freqIndex * 2].toInt())
        
        // 如果可能，应用频率平滑 - 获取相邻频率并计算加权平均值
        var smoothedMagnitude = mainMagnitude.toFloat()
        
        // 获取前一个和后一个频率点(如果存在)
        if (freqIndex > 0 && freqIndex < frequencyRange - 1) {
            val prevMagnitude = abs(displayData[(freqIndex - 1) * 2].toInt())
            val nextMagnitude = abs(displayData[(freqIndex + 1) * 2].toInt())
            
            // 应用加权平均
            smoothedMagnitude = (prevMagnitude * 0.25f + mainMagnitude * 0.5f + nextMagnitude * 0.25f)
        }
        
        // 应用非线性缩放以更好地表现强度变化
        if (smoothedMagnitude > 0) {
            (1 + log10(smoothedMagnitude)) * smoothedMagnitude / 1.5f
        } else {
            0f
        }
    }
    
    // 找出当前帧中的最大幅值，用于动态缩放
    val maxMagnitude = bandMagnitudes.maxOrNull() ?: 1f
    // 设置最大显示高度为画布高度的90%
    val maxBarHeight = canvasHeight * 0.75f
    
    // 绘制频谱条形
    bandMagnitudes.forEachIndexed { i, magnitude ->
        // 计算相对于最大幅值的比例高度
        val heightRatio = if (maxMagnitude > 0) magnitude / maxMagnitude else 0f
        
        // 使用相对比例计算条形高度，确保即使低幅度也有最小高度
        val scaledHeight = max(
            4f, // 最小高度，确保始终可见
            heightRatio * maxBarHeight
        )
        
        // 计算条形位置
        val left = i * barWidth
        val top = canvasHeight - scaledHeight
        
        // 计算渐变颜色 - 基于相对强度而非绝对值
        val normalizedMagnitude = heightRatio.coerceIn(0f, 1f)
        val colorPosition = sqrt(normalizedMagnitude) // 使用平方根使颜色分布更均匀
        val colorIndex = (colorPosition * (gradientColors.size - 1)).toInt()
        val nextColorIndex = (colorIndex + 1).coerceAtMost(gradientColors.size - 1)
        
        val colorFraction = (colorPosition * (gradientColors.size - 1)) - colorIndex
        
        val r = lerp(
            gradientColors[colorIndex].red, 
            gradientColors[nextColorIndex].red, 
            colorFraction
        )
        val g = lerp(
            gradientColors[colorIndex].green, 
            gradientColors[nextColorIndex].green, 
            colorFraction
        )
        val b = lerp(
            gradientColors[colorIndex].blue, 
            gradientColors[nextColorIndex].blue, 
            colorFraction
        )
        
        // 如果暂停，调低颜色亮度
        val alpha = if (isPlaying) 1.0f else 0.7f
        val barColor = Color(r, g, b, alpha)
        
        // 圆角条形使显示更平滑
        val cornerRadius = barWidth * 0.2f
        
        // 绘制条形
        drawRoundRect(
            color = barColor,
            topLeft = androidx.compose.ui.geometry.Offset(left + barWidth * 0.1f, top),
            size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, scaledHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
        )
        
        // 在条形顶部添加发光效果
        val glowColor = barColor.copy(alpha = 0.7f)
        drawCircle(
            color = glowColor,
            radius = barWidth * 0.4f,
            center = androidx.compose.ui.geometry.Offset(
                left + barWidth * 0.5f,
                top
            )
        )
    }
}

// 绘制波形频谱
private fun DrawScope.drawWaveform(
    displayData: ByteArray,
    isPlaying: Boolean,
    gradient: Brush,
    backgroundColor: Color
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    
    // 中心线背景 - 使用半透明主题色调
    drawRect(
        color = backgroundColor,
        size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight)
    )
    
    // 使用FFT数据的前半部分，优化频率范围的展示
    val frequencyRange = min(displayData.size / 2, 128)
    
    // 创建波形路径
    val path = Path()
    val mirrorPath = Path() // 镜像波形，创建完整的波浪效果
    
    // 中心线
    val centerY = canvasHeight / 2
    
    // 优化频带展示
    val frequencyBands = 64
    val bandIndices = List(frequencyBands) { i ->
        val t = i.toFloat() / (frequencyBands - 1)
        val exponentialT = t.pow(1.5f) // 稍微非线性
        (exponentialT * (frequencyRange - 1)).toInt()
    }
    
    // 计算每个点的x坐标
    val xStep = canvasWidth / (frequencyBands - 1)
    
    // 获取频带的幅值，应用平滑处理
    val bandMagnitudes = bandIndices.mapIndexed { index, freqIndex ->
        val magnitude = abs(displayData[freqIndex * 2].toInt())
        
        // 应用对数缩放
        val logMagnitude = if (magnitude > 0) {
            (1 + log10(magnitude.toFloat())) * magnitude / 2f
        } else {
            0f
        }
        
        // 缩放幅度以适应画布高度的一半
        (logMagnitude / 128f) * (canvasHeight * 0.4f)
    }
    
    // 创建平滑波形
    path.moveTo(0f, centerY - bandMagnitudes[0])
    mirrorPath.moveTo(0f, centerY + bandMagnitudes[0])
    
    for (i in 1 until frequencyBands) {
        val x = i * xStep
        val y = centerY - bandMagnitudes[i]
        val mirrorY = centerY + bandMagnitudes[i]
        
        // 使用贝塞尔曲线使波形更平滑
        if (i > 1) {
            val prevX = (i - 1) * xStep
            val midX = (prevX + x) / 2
            
            path.quadraticBezierTo(
                prevX, centerY - bandMagnitudes[i - 1],
                midX, (centerY - bandMagnitudes[i - 1] + centerY - bandMagnitudes[i]) / 2
            )
            
            mirrorPath.quadraticBezierTo(
                prevX, centerY + bandMagnitudes[i - 1],
                midX, (centerY + bandMagnitudes[i - 1] + centerY + bandMagnitudes[i]) / 2
            )
        }
        
        if (i == frequencyBands - 1) {
            path.lineTo(x, y)
            mirrorPath.lineTo(x, mirrorY)
        }
    }
    
    // 闭合路径以填充
    path.lineTo(canvasWidth, centerY)
    path.lineTo(0f, centerY)
    path.close()
    
    mirrorPath.lineTo(canvasWidth, centerY)
    mirrorPath.lineTo(0f, centerY)
    mirrorPath.close()
    
    // 绘制填充波形
    val alpha = if (isPlaying) 0.9f else 0.6f
    
    drawPath(
        path = path,
        brush = gradient,
        alpha = alpha
    )
    
    drawPath(
        path = mirrorPath,
        brush = gradient,
        alpha = alpha * 0.7f // 镜像稍微透明一些
    )
    
    // 绘制波形轮廓
    drawPath(
        path = path,
        color = Color(0xFFFFFFFF),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.5f,
            cap = StrokeCap.Round
        ),
        alpha = alpha * 0.8f
    )
    
    drawPath(
        path = mirrorPath,
        color = Color(0xFFFFFFFF),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.5f,
            cap = StrokeCap.Round
        ),
        alpha = alpha * 0.5f
    )
}

// 绘制圆形频谱
private fun DrawScope.drawCircular(
    displayData: ByteArray,
    isPlaying: Boolean,
    gradientColors: List<Color>,
    backgroundColor: Color
) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    
    // 圆心
    val centerX = canvasWidth / 2
    val centerY = canvasHeight / 2
    
    // 圆形半径
    val baseRadius = min(canvasWidth, canvasHeight) * 0.30f
    
    // 绘制柔和的背景圆
    drawCircle(
        color = backgroundColor,
        radius = baseRadius * 1.2f,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
    
    // 使用FFT数据的前半部分
    val frequencyRange = min(displayData.size / 2, 128)
    
    // 定义频带数量和角度步长
    val frequencyBands = 72 // 增加数量使圆更平滑
    val angleStep = (2 * Math.PI / frequencyBands).toFloat()
    
    // 优化频带分布，使用非线性映射
    val bandIndices = List(frequencyBands) { i ->
        val t = i.toFloat() / (frequencyBands - 1)
        val exponentialT = t.pow(1.8f) // 使用更高的幂可使低频获得更多细节
        (exponentialT * (frequencyRange - 1)).toInt()
    }
    
    // 获取频带的幅值，应用平滑处理
    val bandMagnitudes = bandIndices.mapIndexed { index, freqIndex ->
        val magnitude = abs(displayData[freqIndex * 2].toInt())
        
        // 应用对数缩放
        val logMagnitude = if (magnitude > 0) {
            (1 + log10(magnitude.toFloat())) * magnitude / 2f
        } else {
            0f
        }
        
        // 缩放幅度以适应圆形可视化
        (logMagnitude / 128f) * baseRadius * 0.8f
    }
    
    // 创建圆形频谱路径
    val path = Path()
    val points = mutableListOf<androidx.compose.ui.geometry.Offset>()
    
    for (i in 0 until frequencyBands) {
        val angle = i * angleStep
        val magnitude = bandMagnitudes[i % bandMagnitudes.size]
        val radius = baseRadius + magnitude
        
        val x = centerX + radius * kotlin.math.cos(angle)
        val y = centerY + radius * kotlin.math.sin(angle)
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
        
        points.add(androidx.compose.ui.geometry.Offset(x, y))
    }
    
    path.close()
    
    // 绘制内部填充
    val alpha = if (isPlaying) 0.8f else 0.5f
    
    // 创建径向渐变
    val radialGradient = Brush.radialGradient(
        colors = listOf(
            gradientColors.first().copy(alpha = 0.7f),  // 使用主题渐变色第一种颜色
            gradientColors.last().copy(alpha = 0.5f)    // 使用主题渐变色最后一种颜色
        ),
        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
        radius = baseRadius + bandMagnitudes.maxOrNull()!! + 50f
    )
    
    // 绘制基础圆形
    drawCircle(
        color = backgroundColor.copy(alpha = 0.8f),
        radius = baseRadius,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
    
    // 绘制填充圆形频谱
    drawPath(
        path = path,
        brush = radialGradient,
        alpha = alpha
    )
    
    // 绘制轮廓
    drawPath(
        path = path,
        color = Color(0xFFE0E0E0),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2f
        ),
        alpha = alpha * 0.9f
    )
    
    // 添加发光点
    points.forEachIndexed { index, offset ->
        val pointMagnitude = bandMagnitudes[index % bandMagnitudes.size]
        
        // 只为幅度较大的点添加发光效果
        if (pointMagnitude > baseRadius * 0.1f) {
            val normalizedMagnitude = (pointMagnitude / (baseRadius * 0.8f)).coerceIn(0f, 1f)
            val colorIndex = (normalizedMagnitude * (gradientColors.size - 1)).toInt()
            
            drawCircle(
                color = gradientColors[colorIndex],
                radius = 3f + (normalizedMagnitude * 4f),
                center = offset,
                alpha = alpha * normalizedMagnitude
            )
        }
    }
    
    // 中心小圆
    drawCircle(
        color = Color(0xFFE0E0E0),
        radius = baseRadius * 0.1f,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
        alpha = 0.7f
    )
}

// 线性插值函数
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
} 
