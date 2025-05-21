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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    
    // State for the slider to avoid constant updates
    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Horizontal scroll state for song title
    val titleScrollState = rememberScrollState()
    
    // Auto-scroll for long titles
    LaunchedEffect(musicFile.title, titleScrollState.maxValue) {
        if (titleScrollState.maxValue > 0) {
            // Only auto-scroll if the title is longer than available space
            delay(1500) // Initial delay before scrolling
            titleScrollState.animateScrollTo(titleScrollState.maxValue)
            delay(1500) // Pause at the end
            titleScrollState.animateScrollTo(0)
        }
    }
    
    // Update slider position when not dragging
    LaunchedEffect(currentPosition, duration, isPlaying) {
        if (!isDragging && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration.coerceAtLeast(1)
        }
    }
    
    // Polling to update position during playback
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                delay(500) // Update every 500ms for smoother UI
                if (!isDragging && duration > 0) {
                    sliderPosition = currentPosition.toFloat() / duration.coerceAtLeast(1)
                }
            }
        }
    }
    
    // Sample lyrics (would be fetched from a real source)
    val lyrics = remember {
        """
        This is where the lyrics would appear
        Line by line as the song plays
        
        In a real app, these would be synced
        To the current playback position
        
        And would scroll automatically
        As the song progresses through verses
        
        You could highlight the current line
        To make it easier to follow along
        """.trimIndent()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
                // Album artwork
                Card(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Use our music placeholder drawable
                        Image(
                            painter = painterResource(id = R.drawable.music_placeholder),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Song title container with fixed width
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f) // Use 90% of available width
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    // Scrollable song title
                    Text(
                        text = musicFile.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.horizontalScroll(titleScrollState),
                        // No maxLines or overflow to allow scrolling
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Artist name (with ellipsis for long text)
                Text(
                    text = musicFile.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Playback mode selector
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
                
                // Playback progress
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Progress slider
                    Slider(
                        value = sliderPosition,
                        onValueChange = { 
                            sliderPosition = it
                            isDragging = true
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            // Convert slider value to position in milliseconds
                            val newPosition = (sliderPosition * duration).toInt()
                            musicPlayerManager.seekTo(newPosition)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                    
                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Current position
                        Text(
                            text = formatDuration(currentPosition.toLong()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        // Total duration
                        Text(
                            text = formatDuration(duration.toLong()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip previous
                    IconButton(
                        onClick = { musicPlayerManager.playPreviousSong() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Play/Pause
                    IconButton(
                        onClick = { musicPlayerManager.playPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) 
                                Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    // Skip next
                    IconButton(
                        onClick = { musicPlayerManager.playNextSong() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// Helper function to format duration
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
} 