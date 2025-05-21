package com.example.myapp.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.myapp.ui.music.SpectrumAnalyzer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapp.R
import com.example.myapp.music.MusicPlayerManager
import com.example.myapp.music.PlaybackMode
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailScreen(
    navController: NavController,
    musicPlayerManager: MusicPlayerManager,
    musicFile: MusicFile
) {
    val context = LocalContext.current
    val isPlaying by musicPlayerManager.isPlaying.collectAsState()
    val currentPosition by musicPlayerManager.currentPosition.collectAsState()
    val duration by musicPlayerManager.duration.collectAsState()
    val playbackMode by musicPlayerManager.playbackMode.collectAsState()
    val fftData by musicPlayerManager.fftData.collectAsState()

    //滑块的状态，以避免不断更新
    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    //滑块的状态，以避免不断更新
    val titleScrollState = rememberScrollState()

    //自动滚动长标题
    LaunchedEffect(musicFile.title, titleScrollState.maxValue) {
        if (titleScrollState.maxValue > 0) {
            // 仅当标题长度超过可用空间时才自动滚动
            delay(1500) // 滚动前的初始延迟
            titleScrollState.animateScrollTo(titleScrollState.maxValue)
            delay(1500) // 在末尾暂停
            titleScrollState.animateScrollTo(0)
        }
    }
    
    // 当不拖动时更新滑块位置
    LaunchedEffect(currentPosition, duration, isPlaying) {
        if (!isDragging && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration.coerceAtLeast(1)
        }
    }
    
    // 播放期间轮询更新位置
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                delay(500) // 每500毫秒更新一次，使UI更流畅
                if (!isDragging && duration > 0) {
                    sliderPosition = currentPosition.toFloat() / duration.coerceAtLeast(1)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在播放") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 专辑封面
                Card(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp) // 稍微多一点的内边距
                            .clip(RoundedCornerShape(4.dp)), // 内部边角也是圆角
                        contentAlignment = Alignment.Center
                    ) {
                        // 频谱背景
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {}
                        
                        // 频谱可视化
                        SpectrumAnalyzer(
                            fftData = fftData,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 2.dp), // 添加内部填充
                            barCount = 50, // 更多频率分辨率的条
                            barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            maxBarHeightScale = 0.98f, // 从0.95f增加以填充更多盒子
                            minBarHeight = 4.dp, // 从2.dp增加以使小条更加可见
                            smoothingFactor = 0.3f, // 更高的平滑因子
                            dynamicResponseSpeed = 0.7f, // 对变化的更快响应
                            isPlaying = isPlaying // 传递isPlaying状态控制可视化
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 歌曲标题容器，固定宽度
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f) // 使用90%的可用宽度
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    // 可滚动的歌曲标题
                    Text(
                        text = musicFile.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.horizontalScroll(titleScrollState),
                        // 没有maxLines或overflow以允许滚动
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 艺术家名称（长文本用省略号）
                Text(
                    text = musicFile.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 播放模式选择器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RepeatOne,
                        contentDescription = null,
                        tint = if (playbackMode == PlaybackMode.SEQUENTIAL) 
                            MaterialTheme.colorScheme.primary else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    Switch(
                        checked = playbackMode == PlaybackMode.RANDOM,
                        onCheckedChange = { isRandom ->
                            musicPlayerManager.setPlaybackMode(
                                if (isRandom) PlaybackMode.RANDOM else PlaybackMode.SEQUENTIAL
                            )
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = if (playbackMode == PlaybackMode.RANDOM) 
                            MaterialTheme.colorScheme.primary else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 播放进度
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 进度滑块
                    Slider(
                        value = sliderPosition,
                        onValueChange = { 
                            sliderPosition = it
                            isDragging = true
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            // 将滑块值转换为毫秒位置
                            val newPosition = (sliderPosition * duration).toInt()
                            musicPlayerManager.seekTo(newPosition)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                    
                    // 时间显示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 当前位置
                        Text(
                            text = formatDuration(currentPosition.toLong()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        // 总时长
                        Text(
                            text = formatDuration(duration.toLong()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 播放控制
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 上一首
                    IconButton(
                        onClick = { musicPlayerManager.playPreviousSong() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "上一首",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // 播放/暂停
                    IconButton(
                        onClick = { musicPlayerManager.playPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) 
                                Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    // 下一首
                    IconButton(
                        onClick = { musicPlayerManager.playNextSong() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// 格式化时长的辅助函数
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
} 