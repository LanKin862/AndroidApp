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

    private val _fftData = MutableStateFlow<ByteArray?>(null)
    val fftData: StateFlow<ByteArray?> = _fftData.asStateFlow()

    private var visualizer: Visualizer? = null
    
    // 模拟的FFT数据
    private var simulationThread: Thread? = null
    private var useSimulation = false
    private val simulatedFftSize = 128 // 模拟FFT数据的大小
    
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
        releaseVisualizer()
        mediaPlayer?.release()
        stopPositionTracking()
        stopSimulation()
        
        // 创建新的MediaPlayer
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
            start()
            
            // 更新状态
            _isPlaying.value = true
            _currentSongTitle.value = title
            _currentSongArtist.value = artist
            _duration.value = duration
            
            // 设置完成监听器
            setOnCompletionListener {
                _isPlaying.value = false
                stopPositionTracking()
                stopSimulation()
                // 当前歌曲完成后自动播放下一首
                playNextSong()
            }
        }
        
        // 开始位置跟踪
        startPositionTracking()

        // 设置可视化器
        val sessionId = mediaPlayer?.audioSessionId
        Log.d(TAG, "音频会话ID: $sessionId")

        if (sessionId != null && sessionId != 0) {
            try {
                Log.d(TAG, "尝试使用音频会话ID初始化可视化器: $sessionId")
                visualizer = Visualizer(sessionId).apply {
                    // 设置捕获大小为2的幂以获得更好的FFT结果
                    captureSize = 1024
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(viz: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                        override fun onFftDataCapture(viz: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null) {
                                // 处理FFT数据以获取幅度值
                                val processedFft = ByteArray(fft.size / 2)
                                for (i in 0 until fft.size / 2) {
                                    val real = fft[i * 2].toFloat()
                                    val imag = fft[i * 2 + 1].toFloat()
                                    // 计算幅度并缩放
                                    val magnitude = Math.sqrt((real * real + imag * imag).toDouble()).toFloat()
                                    // 将幅度缩放到字节范围(0-255)
                                    processedFft[i] = (magnitude * 255).toInt().coerceIn(0, 255).toByte()
                                }
                                _fftData.value = processedFft
                            }
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    enabled = true
                }
                Log.i(TAG, "可视化器成功初始化，音频会话ID: $sessionId。捕获大小: ${visualizer?.captureSize}")
                useSimulation = false
            } catch (e: Exception) {
                Log.e(TAG, "为音频会话ID初始化可视化器时出错: $sessionId", e)
                visualizer = null
                useSimulation = true
                startSimulation()
            }
        } else {
            Log.w(TAG, "无法初始化可视化器: 无效的音频会话ID ($sessionId)")
            useSimulation = true
            startSimulation()
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
                pauseSimulation()
            } else {
                it.start()
                _isPlaying.value = true
                startPositionTracking()
                visualizer?.enabled = true
                resumeSimulation()
            }
        }
    }
    
    // 停止播放
    fun stop() {
        releaseVisualizer()
        stopSimulation()
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
        }
    }
    
    // 跳转到指定位置
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
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
                    Thread.sleep(1000) // 现有的休眠
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
        releaseVisualizer()
        stopSimulation()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentSongTitle.value = null
        _currentSongArtist.value = null
        stopPositionTracking()
    }

    private fun releaseVisualizer() {
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
        _fftData.value = null // 清除FFT数据
    }
    
    // FFT数据模拟方法
    private fun startSimulation() {
        if (!useSimulation) return
        stopSimulation()
        
        Log.d(TAG, "开始FFT数据模拟")
        simulationThread = Thread {
            try {
                // 不同频率范围的因子，具有压缩的动态范围
                val frequencyBandFactors = floatArrayOf(
                    1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.5f,  // 超低音 (20-60Hz)
                    1.4f, 1.3f, 1.2f, 1.1f, 1.0f, 0.9f, 0.85f, 0.8f,  // 低音 (60-250Hz)
                    0.75f, 0.7f, 0.65f, 0.6f, 0.55f, 0.5f, 0.55f, 0.6f,  // 低中音 (250-500Hz)
                    0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.85f, 0.8f,  // 中音 (500-2kHz)
                    0.75f, 0.7f, 0.65f, 0.6f, 0.55f, 0.5f, 0.45f, 0.4f,  // 中高音 (2-4kHz)
                    0.35f, 0.3f, 0.25f, 0.2f, 0.15f, 0.1f, 0.05f, 0.05f   // 高音 (4-20kHz)
                )
                
                // 每个频带的基础动画速度（高频率更多变化）
                val bandAnimationSpeeds = floatArrayOf(
                    0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.55f, 0.6f, 0.65f,  // 超低音
                    0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.05f,  // 低音
                    1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f, 1.45f,  // 低中音
                    1.5f, 1.55f, 1.6f, 1.65f, 1.7f, 1.75f, 1.8f, 1.85f,  // 中音
                    1.9f, 1.95f, 2.0f, 2.05f, 2.1f, 2.15f, 2.2f, 2.25f,  // 中高音
                    2.3f, 2.35f, 2.4f, 2.45f, 2.5f, 2.55f, 2.6f, 2.65f   // 高音
                )
                
                // 确保数组匹配我们模拟的大小
                val adjustedBandFactors = if (frequencyBandFactors.size > simulatedFftSize) {
                    frequencyBandFactors.sliceArray(0 until simulatedFftSize)
                } else {
                    frequencyBandFactors
                }
                
                val adjustedAnimationSpeeds = if (bandAnimationSpeeds.size > simulatedFftSize) {
                    bandAnimationSpeeds.sliceArray(0 until simulatedFftSize)
                } else {
                    bandAnimationSpeeds
                }
                
                // 跟踪每个频率波段动画相位的值
                val phases = FloatArray(simulatedFftSize) { 0f }
                
                // 高频的独立随机偏移
                val highFreqOffsets = FloatArray(simulatedFftSize) { 
                    if (it >= simulatedFftSize * 0.6f) (Math.random() * Math.PI * 2).toFloat() else 0f 
                }
                
                // 获取初始时间戳
                var lastTime = System.currentTimeMillis()
                
                while (!Thread.currentThread().isInterrupted) {
                    if (_isPlaying.value) {
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = (currentTime - lastTime) / 1000f // 以秒为单位的时间差
                        lastTime = currentTime
                        
                        // 基于当前位置和时间生成模拟的FFT数据
                        val simulatedData = ByteArray(simulatedFftSize)
                        val currentPos = _currentPosition.value
                        val duration = _duration.value
                        val songProgress = currentPos.toFloat() / duration.coerceAtLeast(1)
                        
                        // 为每个频率波段创建动态模式
                        for (i in 0 until simulatedFftSize) {
                            // 更新这个频率波段的相位
                            phases[i] += deltaTime * adjustedAnimationSpeeds.getOrElse(i) { 1.0f } * 2f
                            
                            // 确定这是否是高频波段
                            val isHighFreq = i >= simulatedFftSize * 0.6f
                            
                            // 偶尔更新高频偏移
                            if (isHighFreq && Math.random() < 0.02) { // 每帧2%的概率
                                highFreqOffsets[i] = (Math.random() * Math.PI * 2).toFloat()
                            }
                            
                            // 使用几个不同相位的正弦波的基本模式
                            val baseSine = Math.sin(phases[i] * Math.PI + highFreqOffsets[i]).toFloat()
                            
                            // 高频与低频不同行为的次级波
                            val secondarySine = if (isHighFreq) {
                                // 高频有更独立的运动
                                Math.sin((phases[i] * 2.5f + highFreqOffsets[i]) * Math.PI).toFloat() * 0.4f
                            } else {
                                // 低频更受歌曲位置影响
                                Math.sin((phases[i] * 1.5f + songProgress * 3f) * Math.PI).toFloat() * 0.5f
                            }
                            
                            val tertiarySine = if (isHighFreq) {
                                // 高频有更独立的运动
                                Math.sin((phases[i] * 1.2f + highFreqOffsets[i] * 2f) * Math.PI).toFloat() * 0.2f
                            } else {
                                // 低频更受歌曲位置影响
                                Math.sin((phases[i] * 0.8f + songProgress * 5f) * Math.PI).toFloat() * 0.3f
                            }
                            
                            // 以不同权重组合波
                            val combinedWave = (baseSine + secondarySine + tertiarySine) / 1.8f
                            
                            // 应用频率波段因子（该波段中有多少能量）
                            val bandFactor = adjustedBandFactors.getOrElse(i) { 1.0f }
                            
                            // 添加随机变化 - 高频更多
                            val randomFactor = if (isHighFreq) {
                                (Math.random() * 0.5).toFloat() // 更多随机性
                            } else {
                                (Math.random() * 0.3).toFloat() // 更少随机性
                            }
                            
                            // 计算原始值
                            val rawValue = (combinedWave * 0.7f + randomFactor) * bandFactor
                            
                            // 应用对数压缩以减少动态范围
                            val compressedValue = if (rawValue > 0) {
                                val logBase = 10f
                                val compressionFactor = 0.5f
                                (1f + compressionFactor * (Math.log10((1f + rawValue * (logBase - 1f)).toDouble()) /
                                    Math.log10(logBase.toDouble()))).toFloat() - 1f
                            } else {
                                0f
                            }
                            
                            // 缩放到字节范围
                            val value = (compressedValue * 255)
                                .toInt()
                                .coerceIn(5, 255) // 确保至少有一些最小值
                            
                            simulatedData[i] = value.toByte()
                        }
                        
                        // 基于歌曲位置创建节拍模式 - 只影响低频
                        val beatFrequency = 0.5f + (songProgress * 1.5f) // 随着歌曲进展，节拍变快
                        val beatPhase = (currentTime / 1000f) * beatFrequency * Math.PI.toFloat()
                        val beatStrength = (Math.sin(beatPhase.toDouble()) * 0.5 + 0.5).toFloat()
                        
                        // 突出节拍上的低频 - 但不是高频
                        for (i in 0 until (simulatedFftSize * 0.3f).toInt()) {
                            val value = simulatedData[i].toInt() and 0xFF
                            val newValue = (value * (1f + beatStrength * 0.7f)).toInt().coerceIn(0, 255)
                            simulatedData[i] = newValue.toByte()
                        }
                        
                        _fftData.value = simulatedData
                    }
                    
                    // 限制更新率以避免过度使用CPU
                    Thread.sleep(33) // ~30fps
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "模拟线程被中断")
            } catch (e: Exception) {
                Log.e(TAG, "模拟线程出错", e)
            }
        }.apply {
            start()
        }
    }
    
    private fun stopSimulation() {
        simulationThread?.interrupt()
        simulationThread = null
    }
    
    private fun pauseSimulation() {
        // 只停止更新但不杀死线程
        useSimulation = false
    }
    
    private fun resumeSimulation() {
        if (visualizer == null) {
            useSimulation = true
            if (simulationThread == null) {
                startSimulation()
            }
        }
    }
} 