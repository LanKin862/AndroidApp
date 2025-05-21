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
    
    // Current playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // Current song info
    private val _currentSongTitle = MutableStateFlow<String?>(null)
    val currentSongTitle: StateFlow<String?> = _currentSongTitle.asStateFlow()
    
    private val _currentSongArtist = MutableStateFlow<String?>(null)
    val currentSongArtist: StateFlow<String?> = _currentSongArtist.asStateFlow()
    
    // Current playback position
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    // Song duration
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _fftData = MutableStateFlow<ByteArray?>(null)
    val fftData: StateFlow<ByteArray?> = _fftData.asStateFlow()

    private var visualizer: Visualizer? = null
    
    // Playlist management
    private val _playlist = MutableStateFlow<List<MusicFile>>(emptyList())
    val playlist: StateFlow<List<MusicFile>> = _playlist.asStateFlow()
    
    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()
    
    // Playback mode
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()
    
    // Set playlist and start at first song
    fun setPlaylist(musicFiles: List<MusicFile>, startIndex: Int = 0) {
        _playlist.value = musicFiles
        
        if (musicFiles.isNotEmpty() && startIndex >= 0 && startIndex < musicFiles.size) {
            _currentSongIndex.value = startIndex
            playCurrentSong()
        }
    }
    
    // Set playback mode
    fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
    }
    
    // Toggle playback mode
    fun togglePlaybackMode() {
        _playbackMode.value = if (_playbackMode.value == PlaybackMode.SEQUENTIAL) {
            PlaybackMode.RANDOM
        } else {
            PlaybackMode.SEQUENTIAL
        }
    }
    
    // Play a specific song by index
    private fun playCurrentSong() {
        val currentIndex = _currentSongIndex.value
        if (currentIndex >= 0 && currentIndex < _playlist.value.size) {
            val musicFile = _playlist.value[currentIndex]
            playSong(musicFile.uri, musicFile.title, musicFile.artist)
        }
    }
    
    // Play next song based on current mode
    fun playNextSong() {
        val currentPlaylist = _playlist.value
        if (currentPlaylist.isEmpty()) return
        
        val nextIndex = when (_playbackMode.value) {
            PlaybackMode.SEQUENTIAL -> {
                // Go to next song, or back to beginning if at end
                (_currentSongIndex.value + 1) % currentPlaylist.size
            }
            PlaybackMode.RANDOM -> {
                // Choose a random song that's not the current one
                if (currentPlaylist.size == 1) {
                    0 // Only one song, so play it again
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
    
    // Play previous song
    fun playPreviousSong() {
        val currentPlaylist = _playlist.value
        if (currentPlaylist.isEmpty()) return
        
        val prevIndex = when (_playbackMode.value) {
            PlaybackMode.SEQUENTIAL -> {
                // Go to previous song, or to end if at beginning
                if (_currentSongIndex.value <= 0) currentPlaylist.size - 1
                else _currentSongIndex.value - 1
            }
            PlaybackMode.RANDOM -> {
                // Choose a random song that's not the current one
                if (currentPlaylist.size == 1) {
                    0 // Only one song, so play it again
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
    
    // Play a song from a URI
    fun playSong(uri: Uri, title: String, artist: String) {
        // Release any existing MediaPlayer
        releaseVisualizer()
        mediaPlayer?.release()
        stopPositionTracking()
        
        // Create a new MediaPlayer
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            prepare()
            start()
            
            // Update state
            _isPlaying.value = true
            _currentSongTitle.value = title
            _currentSongArtist.value = artist
            _duration.value = duration
            
            // Set up completion listener
            setOnCompletionListener {
                _isPlaying.value = false
                stopPositionTracking()
                // Auto-play next song when current one completes
                playNextSong()
            }
        }
        
        // Start position tracking
        startPositionTracking()

        // Visualizer setup
        val sessionId = mediaPlayer?.audioSessionId
        Log.d(TAG, "AudioSessionId: $sessionId")

        if (sessionId != null && sessionId != 0) {
            try {
                Log.d(TAG, "Attempting to initialize Visualizer with audioSessionId: $sessionId")
                visualizer = Visualizer(sessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(viz: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                        override fun onFftDataCapture(viz: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            Log.v(TAG, "onFftDataCapture called. FFT data size: ${fft?.size}")
                            _fftData.value = fft?.clone()
                            Log.v(TAG, "_fftData updated. New size: ${_fftData.value?.size}")
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    enabled = true
                }
                Log.i(TAG, "Visualizer initialized successfully for audioSessionId: $sessionId. Capture size: ${visualizer?.captureSize}")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Visualizer for audioSessionId: $sessionId", e)
                _fftData.value = null
            }
        } else {
            Log.w(TAG, "Cannot initialize Visualizer: Invalid audioSessionId ($sessionId)")
            _fftData.value = null // Ensure data is null if session ID is invalid
        }
    }
    
    // Play/Pause toggle
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
    
    // Stop playback
    fun stop() {
        releaseVisualizer()
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
    
    // Seek to position
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }
    
    // Track playback position
    private fun startPositionTracking() {
        // Stop any existing tracking thread first
        stopPositionTracking()
        
        // Create and start a new tracking thread
        positionTrackingThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted && mediaPlayer != null) {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            _currentPosition.value = it.currentPosition
                        }
                    }
                    Thread.sleep(1000)
                }
            } catch (e: InterruptedException) {
                // Thread was interrupted, exit gracefully
            } catch (e: Exception) {
                // Handle other exceptions
            }
        }.apply {
            start()
        }
    }
    
    // Stop position tracking
    private fun stopPositionTracking() {
        positionTrackingThread?.interrupt()
        positionTrackingThread = null
    }
    
    // Clean up resources when done
    fun release() {
        releaseVisualizer()
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
        _fftData.value = null // Clear FFT data
    }
} 