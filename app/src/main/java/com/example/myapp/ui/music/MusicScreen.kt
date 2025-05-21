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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.FloatingActionButton

// 音乐文件的数据类
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
    
    // 从存储库获取导入的音乐
    val savedImportedMusic by repository.importedMusic.collectAsState(initial = emptyList())
    val importedMusicFiles = remember { mutableStateListOf<MusicFile>() }
    
    // 当屏幕首次显示时加载已保存的导入音乐
    LaunchedEffect(savedImportedMusic) {
        if (savedImportedMusic.isNotEmpty()) {
            importedMusicFiles.clear()
            importedMusicFiles.addAll(savedImportedMusic)
        }
    }
    
    // 当前播放状态观察
    val currentSongTitle by musicPlayerManager.currentSongTitle.collectAsState()
    val currentSongArtist by musicPlayerManager.currentSongArtist.collectAsState()
    val isPlaying by musicPlayerManager.isPlaying.collectAsState()
    val currentSongIndex by musicPlayerManager.currentSongIndex.collectAsState()
    val playlist by musicPlayerManager.playlist.collectAsState()
    
    // 获取当前导航路由
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // 确定是否显示"正在播放"栏
    // 在详情屏幕中隐藏它
    val showNowPlayingBar by remember(currentRoute, currentSongTitle) {
        derivedStateOf {
            currentSongTitle != null && currentRoute != "musicDetail"
        }
    }
    
    // 获取当前播放的音乐文件
    val currentMusicFile = if (currentSongIndex >= 0 && currentSongIndex < playlist.size) {
        playlist[currentSongIndex]
    } else {
        null
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 主要内容区域占据大部分空间
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController, startDestination = "musicHome") {
                composable("musicHome") {
                    EnhancedMusicHomeScreen(
                        navController = navController,
                        importedMusicFiles = importedMusicFiles,
                        onMusicImported = { musicFile ->
                            importedMusicFiles.add(musicFile)
                            // 保存更新的导入音乐
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
                        importedMusicFiles = importedMusicFiles,
                        onDeleteMusic = { filesToDelete ->
                            // 创建一个可变的ID集合以便快速查找要删除的内容
                            val idsToDelete = filesToDelete.map { it.id }.toSet()
                            
                            // 检查删除的文件中是否有当前正在播放的
                            val currentPlaylist = musicPlayerManager.playlist.value
                            val currentIndex = musicPlayerManager.currentSongIndex.value
                            
                            // 检查是否需要更新播放器
                            val isPlayingDeletedSong = currentIndex >= 0 && 
                                currentIndex < currentPlaylist.size && 
                                idsToDelete.contains(currentPlaylist[currentIndex].id)
                            
                            // 从importedMusicFiles中删除文件
                            // 使用带有谓词的removeAll，以便更高效、立即移除
                            importedMusicFiles.removeAll { musicFile -> 
                                idsToDelete.contains(musicFile.id)
                            }
                            
                            // 如果是导入的，则删除物理文件
                            filesToDelete.forEach { musicFile ->
                                if (musicFile.uri.toString().startsWith("file:") && 
                                    musicFile.uri.path?.contains("/files/music_") == true) {
                                    try {
                                        val file = File(musicFile.uri.path!!)
                                        if (file.exists()) {
                                            file.delete()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MusicDelete", "删除文件时出错: ${e.message}")
                                    }
                                }
                            }
                            
                            // 保存更新后的列表
                            coroutineScope.launch {
                                repository.saveImportedMusic(importedMusicFiles)
                            }
                            
                            // 如果删除的歌曲正在播放，则更新播放器
                            if (isPlayingDeletedSong) {
                                // 创建不包含已删除歌曲的新播放列表
                                val updatedPlaylist = currentPlaylist.filter { !idsToDelete.contains(it.id) }
                                
                                if (updatedPlaylist.isNotEmpty()) {
                                    // 设置新的播放列表并尝试保持相似的位置
                                    val newIndex = minOf(currentIndex, updatedPlaylist.size - 1)
                                    musicPlayerManager.setPlaylist(updatedPlaylist, newIndex)
                                } else {
                                    // 如果播放列表现在为空，停止播放
                                    musicPlayerManager.stop()
                                }
                            }
                        }
                    )
                }
                composable("musicDetail") {
                    // 在详情屏幕中显示当前播放的歌曲
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
        
        // 底部的"正在播放"栏（在导航栏上方）
        // 仅在不在详情屏幕时显示
        if (showNowPlayingBar) {
            NowPlayingBar(
                title = currentSongTitle ?: "未知",
                artist = currentSongArtist ?: "未知",
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
                // 歌曲信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    // 带水平滚动的长标题
                    val titleScrollState = rememberScrollState()
                    
                    // 长标题自动滚动
                    LaunchedEffect(title, titleScrollState.maxValue) {
                        if (titleScrollState.maxValue > 0) {
                            delay(1000) // 开始滚动前等待
                            titleScrollState.animateScrollTo(titleScrollState.maxValue)
                            delay(1000) // 在末尾暂停
                            titleScrollState.animateScrollTo(0)
                            delay(3000) // 重新开始动画前等待更长时间
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
                
                // 播放/暂停按钮
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 导入进度跟踪状态
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0) }
    var totalFilesToImport by remember { mutableStateOf(0) }
    
    // 文件选择器，用于导入音乐 - 使用GetMultipleContents替代GetContent
    val musicFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        if (uris != null && uris.isNotEmpty()) {
            isImporting = true
            totalFilesToImport = uris.size
            importProgress = 0
            
            coroutineScope.launch {
                try {
                    var successCount = 0
                    
                    // 在后台处理文件
                    withContext(Dispatchers.IO) {
                        uris.forEach { selectedUri ->
                            val musicFile = importMusicFile(context, selectedUri)
                            if (musicFile != null) {
                                withContext(Dispatchers.Main) {
                                    onMusicImported(musicFile)
                                    successCount++
                                }
                            }
                            importProgress++
                        }
                    }
                    
                    // 显示导入结果
                    snackbarHostState.showSnackbar(
                        message = "已导入 ${uris.size} 个文件中的 $successCount 个"
                    )
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(
                        message = "导入文件时出错: ${e.message}"
                    )
                } finally {
                    isImporting = false
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { navController.navigate("localMusic") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("本地音乐")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    musicFilePicker.launch("audio/*") 
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting
            ) {
                Text("导入音乐（可多选）")
            }
            
            // 显示导入进度
            if (isImporting) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在导入 $totalFilesToImport 个文件中的第 $importProgress 个...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 显示已导入音乐数量
            if (importedMusicFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "已导入音乐: ${importedMusicFiles.size} 个文件",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun LocalMusicScreen(
    navController: NavController,
    musicPlayerManager: MusicPlayerManager,
    importedMusicFiles: List<MusicFile>,
    onDeleteMusic: (List<MusicFile>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var deviceMusicFiles by remember { mutableStateOf<List<MusicFile>>(emptyList()) }
    var permissionGranted by remember { mutableStateOf(false) }
    
    // 添加刷新触发状态
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 已选择音乐文件的状态
    val selectedMusicFiles = remember { mutableStateListOf<MusicFile>() }
    
    // 设备和导入音乐的组合列表
    val allMusicFiles = remember(deviceMusicFiles, importedMusicFiles, refreshTrigger) {
        deviceMusicFiles + importedMusicFiles
    }
    
    // 观察当前歌曲标题以高亮正在播放的歌曲
    val currentSongTitle by musicPlayerManager.currentSongTitle.collectAsState()
    
    // 权限请求启动器
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permissionGranted = isGranted
        if (isGranted) {
            deviceMusicFiles = loadMusicFiles(context)
        }
    }
    
    // 当屏幕首次显示时请求权限
    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    // 跟踪是否处于选择模式
    val isInSelectionMode = selectedMusicFiles.isNotEmpty()
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isInSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已选择 ${selectedMusicFiles.size} 项",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row {
                        IconButton(onClick = { selectedMusicFiles.clear() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清除选择"
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                if (selectedMusicFiles.isNotEmpty()) {
                                    musicPlayerManager.setPlaylist(selectedMusicFiles.toList(), 0)
                                    navController.navigate("musicDetail")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放选中项"
                            )
                        }
                        IconButton(
                            onClick = {
                                if(selectedMusicFiles.isNotEmpty()) {
                                    val count = selectedMusicFiles.size
                                    // 使用已选择的文件调用onDeleteMusic回调
                                    onDeleteMusic(selectedMusicFiles.toList())
                                    
                                    // 清除选择
                                    selectedMusicFiles.clear()
                                    musicPlayerManager.setPlaylist(importedMusicFiles, 0)
                                    musicPlayerManager.release()
                                    // 强制刷新UI
                                    refreshTrigger += 1
                                    
                                    // 显示确认信息
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "已删除 $count 个音乐文件"
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "删除选中项"
                            )
                        }
                    }
                }
            } else {
                // 不在选择模式时显示多选提示
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "长按以选择多首歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        floatingActionButton = {
            // 在选择模式下显示"全选"浮动按钮
            if (isInSelectionMode && allMusicFiles.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (selectedMusicFiles.size < allMusicFiles.size) {
                            // 选择所有尚未选择的文件
                            selectedMusicFiles.clear()
                            selectedMusicFiles.addAll(allMusicFiles)
                        } else {
                            // 取消全选
                            selectedMusicFiles.clear()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = if (selectedMusicFiles.size < allMusicFiles.size) 
                            "全选" else "取消全选"
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (permissionGranted) {
                if (allMusicFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到音乐文件。请导入一些音乐！")
                    }
                } else {
                    LazyColumn {
                        items(allMusicFiles) { music ->
                            val isSelected = selectedMusicFiles.contains(music)
                            @OptIn(ExperimentalFoundationApi::class)
                            MusicItem(
                                music = music, 
                                isPlaying = music.title == currentSongTitle,
                                isSelected = isSelected,
                                onClick = {
                                    if (selectedMusicFiles.isEmpty()) {
                                        // 如果不是选择模式，直接播放歌曲
                                        musicPlayerManager.setPlaylist(allMusicFiles, allMusicFiles.indexOf(music))
                                        navController.navigate("musicDetail")
                                    } else {
                                        // 切换选择状态
                                        if (isSelected) {
                                            selectedMusicFiles.remove(music)
                                        } else {
                                            selectedMusicFiles.add(music)
                                        }
                                    }
                                },
                                onLongClick = {
                                    // 启动选择模式
                                    if (!isSelected) {
                                        selectedMusicFiles.add(music)
                                    } else {
                                        selectedMusicFiles.remove(music)
                                    }
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
                    Text("需要存储权限才能访问音乐")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicItem(
    music: MusicFile,
    isPlaying: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // 背景色变化动画
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isPlaying -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor"
    )
    
    // 选择状态的边框宽度动画
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "borderWidth"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick() }
            )
            .padding(8.dp)
            .background(backgroundColor)
            .height(80.dp)
            .border(
                width = borderWidth,
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(4.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已选择",
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.secondary,
                        shape = CircleShape
                    )
                    .padding(4.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                    isPlaying -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = music.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    isPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

// 从设备存储加载音乐文件的函数
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

// 从任何URI导入音乐文件的函数
fun importMusicFile(context: Context, uri: Uri): MusicFile? {
    val contentResolver = context.contentResolver
    
    try {
        // 创建MediaMetadataRetriever提取元数据
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        
        // 提取元数据
        var title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
        var duration = 0L
        
        try {
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationStr != null) {
                duration = durationStr.toLong()
            }
        } catch (e: Exception) {
            Log.e("MusicImport", "解析时长时出错", e)
        }
        
        // 如果元数据中没有标题，尝试获取文件名
        if (title.isNullOrBlank()) {
            // 查询文件元数据获取文件名
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val fileName = cursor.getString(nameIndex)
                        // 移除扩展名作为标题
                        title = fileName.substringBeforeLast(".")
                    }
                }
            }
        }
        
        // 如果仍然没有标题，使用默认值
        if (title.isNullOrBlank()) {
            title = "未知曲目"
        }
        
        // 为导入的文件生成唯一ID
        val id = UUID.randomUUID().mostSignificantBits
        
        // 在应用内部存储中创建文件副本
        val internalFile = copyFileToInternalStorage(context, uri, id.toString())
        val internalUri = Uri.fromFile(internalFile)
        
        return MusicFile(id, title, artist, duration, internalUri)
    } catch (e: Exception) {
        Log.e("MusicImport", "导入音乐文件时出错", e)
        return null
    }
}

// 将文件从URI复制到内部存储的函数
private fun copyFileToInternalStorage(context: Context, sourceUri: Uri, fileName: String): File {
    val inputStream = context.contentResolver.openInputStream(sourceUri)
    val outputFile = File(context.filesDir, "music_$fileName.mp3")
    
    inputStream?.use { input ->
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(4 * 1024) // 4KB缓冲区
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }
    
    return outputFile
} 