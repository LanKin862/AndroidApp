package com.example.myapp.music

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.util.Log
import com.example.myapp.ui.music.MusicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.min

enum class PlaybackMode {
    SEQUENTIAL,
    RANDOM
}

private const val TAG = "MusicPlayerManager"

class MusicPlayerManager(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var positionTrackingThread: Thread? = null
    
    // 当前播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // 当前歌曲信息
    private val _currentSongTitle = MutableStateFlow<String?>(null)
    val currentSongTitle: StateFlow<String?> = _currentSongTitle.asStateFlow()
    
    private val _currentSongArtist = MutableStateFlow<String?>(null)
    val currentSongArtist: StateFlow<String?> = _currentSongArtist.asStateFlow()
    
    // 当前播放位置
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    // 歌曲总时长
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    // FFT音频数据和可视化
    private var visualizer: Visualizer? = null
    private val _fftData = MutableStateFlow(ByteArray(0))
    val fftData: StateFlow<ByteArray> = _fftData.asStateFlow()
    
    // 播放列表管理
    private val _playlist = MutableStateFlow<List<MusicFile>>(emptyList())
    val playlist: StateFlow<List<MusicFile>> = _playlist.asStateFlow()
    
    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()
    
    // 播放模式
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()
    
    // 设置播放列表并从第一首歌开始
    fun setPlaylist(musicFiles: List<MusicFile>, startIndex: Int = 0) {
        _playlist.value = musicFiles
        
        if (musicFiles.isNotEmpty() && startIndex >= 0 && startIndex < musicFiles.size) {
            _currentSongIndex.value = startIndex
            playCurrentSong()
        }
    }
    
    // 设置播放模式
    fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
    }
    
    // 切换播放模式
    fun togglePlaybackMode() {
        _playbackMode.value = if (_playbackMode.value == PlaybackMode.SEQUENTIAL) {
            PlaybackMode.RANDOM
        } else {
            PlaybackMode.SEQUENTIAL
        }
    }
    
    // 通过索引播放特定歌曲
    private fun playCurrentSong() {
        val currentIndex = _currentSongIndex.value
        if (currentIndex >= 0 && currentIndex < _playlist.value.size) {
            val musicFile = _playlist.value[currentIndex]
            playSong(musicFile.uri, musicFile.title, musicFile.artist)
        }
    }
    
    // 基于当前模式播放下一首歌
    fun playNextSong() {
        val currentPlaylist = _playlist.value
        if (currentPlaylist.isEmpty()) return
        
        val nextIndex = when (_playbackMode.value) {
            PlaybackMode.SEQUENTIAL -> {
                // 播放下一首歌，如果到了末尾则回到开头
                (_currentSongIndex.value + 1) % currentPlaylist.size
            }
            PlaybackMode.RANDOM -> {
                // 随机选择一首不是当前歌曲的歌
                if (currentPlaylist.size == 1) {
                    0 // 只有一首歌，继续播放它
                } else {
                    var randomIndex: Int
                    do {
                        randomIndex = Random.nextInt(currentPlaylist.size)
                    } while (randomIndex == _currentSongIndex.value)
                    randomIndex
                }
            }
        }
        
        _currentSongIndex.value = nextIndex
        playCurrentSong()
    }
    
    // 播放上一首歌
    fun playPreviousSong() {
        val currentPlaylist = _playlist.value
        if (currentPlaylist.isEmpty()) return
        
        val prevIndex = when (_playbackMode.value) {
            PlaybackMode.SEQUENTIAL -> {
                // 播放上一首歌，如果在开头则跳到末尾
                if (_currentSongIndex.value <= 0) currentPlaylist.size - 1
                else _currentSongIndex.value - 1
            }
            PlaybackMode.RANDOM -> {
                // 随机选择一首不是当前歌曲的歌
                if (currentPlaylist.size == 1) {
                    0 // 只有一首歌，继续播放它
                } else {
                    var randomIndex: Int
                    do {
                        randomIndex = Random.nextInt(currentPlaylist.size)
                    } while (randomIndex == _currentSongIndex.value)
                    randomIndex
                }
            }
        }
        
        _currentSongIndex.value = prevIndex
        playCurrentSong()
    }
    
    // 从URI播放歌曲
    fun playSong(uri: Uri, title: String, artist: String) {
        // 释放已存在的MediaPlayer
        mediaPlayer?.release()
        stopPositionTracking()
        releaseVisualizer()
        
        try {
            // 创建新的MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                
                // 更新状态信息
                _duration.value = duration
                _currentSongTitle.value = title
                _currentSongArtist.value = artist
            }
            
            // 初始化可视化器 - 在开始播放前设置
            setupVisualizer()
            
            // 延迟一点以确保可视化器完全初始化
            Thread.sleep(50)
            
            // 开始播放
            mediaPlayer?.start()
            _isPlaying.value = true
            
            // 设置完成监听器
            mediaPlayer?.setOnCompletionListener {
                _isPlaying.value = false
                stopPositionTracking()
                // 当前歌曲完成后自动播放下一首
                playNextSong()
            }
            
            // 开始位置跟踪
            startPositionTracking()
            
            Log.d(TAG, "歌曲已开始播放: $title - $artist")
        } catch (e: Exception) {
            Log.e(TAG, "播放歌曲时出错", e)
        }
    }
    
    // 设置音频可视化器
    private fun setupVisualizer() {
        mediaPlayer?.let { player ->
            try {
                // 释放旧的可视化器
                releaseVisualizer()
                
                // 获取有效的会话ID - 使用MediaPlayer的会话ID
                val sessionId = player.audioSessionId
                Log.d(TAG, "设置可视化器: 音频会话ID = $sessionId")
                
                if (sessionId == -1 || sessionId == 0) {
                    Log.e(TAG, "无效的音频会话ID: $sessionId")
                    return
                }
                
                // 创建新的可视化器
                visualizer = Visualizer(sessionId).apply {
                    enabled = false // 配置前禁用
                    
                    // 设置捕获大小（必须是2的幂），以获得良好的频率分辨率
                    captureSize = Visualizer.getCaptureSizeRange()[1] // 使用最大捕获大小
                    
                    Log.d(TAG, "可视化器捕获大小: ${captureSize}, 采样率: ${samplingRate}")
                    
                    // 设置数据捕获监听器
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) {
                            // 不需要处理波形数据
                        }
                        
                        override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) {
                            // 确保数据不为空且有意义
                            if (fft.isNotEmpty()) {
                                var sum = 0
                                for (i in 0 until min(10, fft.size)) {
                                    sum += abs(fft[i].toInt())
                                }
                                
                                // 只在有意义的数据时更新状态
                                if (sum > 0) {
                                    _fftData.value = fft.copyOf() // 使用副本以避免潜在的并发修改问题
                                    
                                    // 减少日志频率
                                    if (Math.random() < 0.05) { // 只记录约5%的更新
                                        Log.d(TAG, "FFT数据: 长度=${fft.size}, 前10个样本平均=${sum/10}")
                                    }
                                } else {
                                    Log.d(TAG, "收到空的FFT数据")
                                }
                            }
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true) // 只捕获FFT数据
                    
                    try {
                        // 配置完成后启用
                        enabled = true
                        Log.d(TAG, "可视化器已启用，开始捕获FFT数据")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "没有使用可视化器的权限", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "启用可视化器时出错", e)
                    }
                }
                
                Log.d(TAG, "音频可视化器已初始化")
            } catch (e: Exception) {
                Log.e(TAG, "初始化可视化器时出错", e)
            }
        } ?: Log.e(TAG, "MediaPlayer为空，无法设置可视化器")
    }
    
    // 释放可视化器资源
    private fun releaseVisualizer() {
        visualizer?.let {
            try {
                it.enabled = false
                it.release()
                visualizer = null
                Log.d(TAG, "已释放可视化器资源")
            } catch (e: Exception) {
                Log.e(TAG, "释放可视化器时出错", e)
            }
        }
    }
    
    // 播放/暂停切换
    fun playPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopPositionTracking()
                visualizer?.enabled = false
            } else {
                it.start()
                _isPlaying.value = true
                startPositionTracking()
                visualizer?.enabled = true
            }
        }
    }
    
    // 停止播放
    fun stop() {
        mediaPlayer?.let {
            it.stop()
            it.release()
            mediaPlayer = null
            _isPlaying.value = false
            _currentSongTitle.value = null
            _currentSongArtist.value = null
            _currentPosition.value = 0
            _duration.value = 0
            stopPositionTracking()
            releaseVisualizer()
        }
    }
    
    // 跳转到指定位置
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
        
        // 如果可视化器处于禁用状态（因为暂停），暂时启用它以获取当前位置的FFT数据
        val isPlaying = mediaPlayer?.isPlaying ?: false
        if (!isPlaying && visualizer?.enabled == false) {
            try {
                // 临时启用可视化器以获取新位置的FFT数据
                visualizer?.enabled = true
                Log.d(TAG, "在寻求位置后临时启用可视化器")
                
                // 延迟100毫秒后再禁用，以便有足够时间捕获FFT数据
                Thread {
                    try {
                        Thread.sleep(100)
                        if (mediaPlayer?.isPlaying == false && visualizer != null) {
                            visualizer?.enabled = false
                            Log.d(TAG, "在捕获FFT数据后禁用可视化器")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "临时启用可视化器时出错", e)
                    }
                }.start()
            } catch (e: Exception) {
                Log.e(TAG, "在寻求位置后启用可视化器时出错", e)
            }
        }
    }
    
    // 跟踪播放位置
    private fun startPositionTracking() {
        stopPositionTracking()
        positionTrackingThread = Thread {
            var lastVisualizerLogTime = 0L // 移动到线程作用域内
            val visualizerLogInterval = 3000L // 明确为Long类型

            try {
                while (!Thread.currentThread().isInterrupted && mediaPlayer != null) {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            _currentPosition.value = it.currentPosition

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastVisualizerLogTime > visualizerLogInterval) {
                                if (visualizer != null) { // 检查可视化器是否为空
                                    Log.d(TAG, "位置跟踪: 可视化器.getEnabled(): ${visualizer?.getEnabled()}")
                                } else {
                                    Log.d(TAG, "位置跟踪: 可视化器为空。")
                                }
                                lastVisualizerLogTime = currentTime
                            }
                        }
                    }
                    Thread.sleep(100) // 更新更频繁，从1000ms降低到100ms，以获得更流畅的位置更新
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "位置跟踪线程被中断。")
            } catch (e: Exception) {
                Log.e(TAG, "位置跟踪线程发生异常", e)
            }
        }.apply {
            start()
        }
    }
    
    // 停止位置跟踪
    private fun stopPositionTracking() {
        positionTrackingThread?.interrupt()
        positionTrackingThread = null
    }
    
    // 完成时清理资源
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentSongTitle.value = null
        _currentSongArtist.value = null
        stopPositionTracking()
        releaseVisualizer()
    }
} 