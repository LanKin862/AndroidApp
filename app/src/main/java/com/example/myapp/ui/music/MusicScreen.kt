package com.example.myapp.ui.music

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapp.data.AppRepository
import com.example.myapp.music.MusicPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

// Data class for music files
data class MusicFile(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri
)

@Composable
fun MusicScreen(
    musicPlayerManager: MusicPlayerManager,
    repository: AppRepository
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Get imported music from repository
    val savedImportedMusic by repository.importedMusic.collectAsState(initial = emptyList())
    val importedMusicFiles = remember { mutableStateListOf<MusicFile>() }
    
    // Load saved imported music when the screen is first displayed
    LaunchedEffect(savedImportedMusic) {
        if (savedImportedMusic.isNotEmpty()) {
            importedMusicFiles.clear()
            importedMusicFiles.addAll(savedImportedMusic)
        }
    }
    
    // Current playback states to observe
    val currentSongTitle by musicPlayerManager.currentSongTitle.collectAsState()
    val currentSongArtist by musicPlayerManager.currentSongArtist.collectAsState()
    val isPlaying by musicPlayerManager.isPlaying.collectAsState()
    val currentSongIndex by musicPlayerManager.currentSongIndex.collectAsState()
    val playlist by musicPlayerManager.playlist.collectAsState()
    
    // Get current navigation route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Determine if we should show the Now Playing bar
    // Hide it when on the detail screen
    val showNowPlayingBar by remember(currentRoute, currentSongTitle) {
        derivedStateOf {
            currentSongTitle != null && currentRoute != "musicDetail"
        }
    }
    
    // Get the currently playing music file
    val currentMusicFile = if (currentSongIndex >= 0 && currentSongIndex < playlist.size) {
        playlist[currentSongIndex]
    } else {
        null
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Main content area takes most of the space
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController, startDestination = "musicHome") {
                composable("musicHome") {
                    MusicHomeScreen(
                        navController = navController,
                        importedMusicFiles = importedMusicFiles,
                        onMusicImported = { musicFile ->
                            importedMusicFiles.add(musicFile)
                            // Save updated imported music
                            coroutineScope.launch {
                                repository.saveImportedMusic(importedMusicFiles)
                            }
                        }
                    )
                }
                composable("localMusic") {
                    LocalMusicScreen(
                        navController = navController,
                        musicPlayerManager = musicPlayerManager,
                        importedMusicFiles = importedMusicFiles
                    )
                }
                composable("musicDetail") {
                    // Show the currently playing song in the detail screen
                    currentMusicFile?.let { music ->
                        MusicDetailScreen(
                            navController = navController,
                            musicPlayerManager = musicPlayerManager,
                            musicFile = music
                        )
                    }
                }
            }
        }
        
        // Now Playing Bar at the bottom (just above the navigation bar)
        // Only show when not on the detail screen
        if (showNowPlayingBar) {
            NowPlayingBar(
                title = currentSongTitle ?: "Unknown",
                artist = currentSongArtist ?: "Unknown",
                isPlaying = isPlaying,
                onPlayPauseClick = { musicPlayerManager.playPause() },
                onBarClick = { navController.navigate("musicDetail") }
            )
        }
    }
}

@Composable
fun NowPlayingBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onBarClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBarClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column {
            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Song info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    // Song title with horizontal scrolling for long titles
                    val titleScrollState = rememberScrollState()
                    
                    // Auto-scroll for long titles
                    LaunchedEffect(title, titleScrollState.maxValue) {
                        if (titleScrollState.maxValue > 0) {
                            delay(1000) // Wait before starting scroll
                            titleScrollState.animateScrollTo(titleScrollState.maxValue)
                            delay(1000) // Pause at the end
                            titleScrollState.animateScrollTo(0)
                            delay(3000) // Wait longer before restarting animation
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            modifier = Modifier.horizontalScroll(titleScrollState)
                        )
                    }
                    
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Play/Pause button
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MusicHomeScreen(
    navController: NavController,
    importedMusicFiles: MutableList<MusicFile>,
    onMusicImported: (MusicFile) -> Unit
) {
    val context = LocalContext.current
    
    // File picker for importing music
    val musicFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val musicFile = importMusicFile(context, selectedUri)
            musicFile?.let { 
                onMusicImported(it)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { navController.navigate("localMusic") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Local Music")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { 
                musicFilePicker.launch("audio/*") 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Import Music")
        }
        
        // Show count of imported music
        if (importedMusicFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Imported Music: ${importedMusicFiles.size} files",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun LocalMusicScreen(
    navController: NavController,
    musicPlayerManager: MusicPlayerManager,
    importedMusicFiles: List<MusicFile>
) {
    val context = LocalContext.current
    var deviceMusicFiles by remember { mutableStateOf<List<MusicFile>>(emptyList()) }
    var permissionGranted by remember { mutableStateOf(false) }
    
    // Combined list of both device and imported music
    val allMusicFiles = remember(deviceMusicFiles, importedMusicFiles) {
        deviceMusicFiles + importedMusicFiles
    }
    
    // Observe current song title to highlight the playing song
    val currentSongTitle by musicPlayerManager.currentSongTitle.collectAsState()
    
    // Permission request launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permissionGranted = isGranted
        if (isGranted) {
            deviceMusicFiles = loadMusicFiles(context)
        }
    }
    
    // Request permission when the screen is first shown
    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (permissionGranted) {
            if (allMusicFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No music files found. Import some music!")
                }
            } else {
                LazyColumn {
                    items(allMusicFiles) { music ->
                        MusicItem(
                            music = music, 
                            isPlaying = music.title == currentSongTitle,
                            onClick = {
                                // Set the full playlist and start with the selected song
                                val selectedIndex = allMusicFiles.indexOf(music)
                                musicPlayerManager.setPlaylist(allMusicFiles, selectedIndex)
                                
                                // Navigate to detail screen
                                navController.navigate("musicDetail")
                            }
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Storage permission required to access music")
            }
        }
    }
}

@Composable
fun MusicItem(
    music: MusicFile,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = music.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Function to load music files from device storage
fun loadMusicFiles(context: Context): List<MusicFile> {
    val musicFiles = mutableListOf<MusicFile>()
    val contentResolver: ContentResolver = context.contentResolver
    
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )
    
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    
    contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn)
            val artist = cursor.getString(artistColumn)
            val duration = cursor.getLong(durationColumn)
            
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )
            
            musicFiles.add(MusicFile(id, title, artist, duration, contentUri))
        }
    }
    
    return musicFiles
}

// Function to import a music file from any URI
fun importMusicFile(context: Context, uri: Uri): MusicFile? {
    val contentResolver = context.contentResolver
    
    // Query the file metadata
    var fileName: String? = null
    var fileSize: Long = 0
    
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
            
            if (sizeIndex != -1) {
                fileSize = cursor.getLong(sizeIndex)
            }
        }
    }
    
    if (fileName == null) {
        return null
    }
    
    // Create a local copy in the app's private directory
    val file = File(context.filesDir, fileName)
    try {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        // Create a content URI for the local file
        val fileUri = Uri.fromFile(file)
        
        // Return a MusicFile object
        return MusicFile(
            id = System.currentTimeMillis(),
            title = fileName ?: "Unknown",
            artist = "Imported",
            duration = 0,
            uri = fileUri
        )
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
} 