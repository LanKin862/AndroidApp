package com.example.myapp.ui.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import androidx.compose.ui.text.font.FontWeight
import android.provider.DocumentsContract
import android.os.Build

@Composable
fun EnhancedMusicHomeScreen(
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
    var successCount by remember { mutableStateOf(0) }
    var skippedCount by remember { mutableStateOf(0) }
    var importJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // 添加跟踪当前会话导入文件的状态
    var currentSessionImports by remember { mutableStateOf(0) }
    
    // 添加跟踪是否向用户显示多选帮助的状态
    var hasShownMultiSelectHelp by remember { mutableStateOf(false) }
    
    // 使用StartActivityForResult而非GetMultipleContents以获得更多控制
    val musicFilePicker = rememberLauncherForActivityResult(
        contract = StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val clipData = data?.clipData
            val uris = mutableListOf<Uri>()
            
            // 通过clipData处理多选
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } 
            // 通过data.data处理单选
            else if (data?.data != null) {
                uris.add(data.data!!)
            }
            
            if (uris.isNotEmpty()) {
                isImporting = true
                totalFilesToImport = uris.size
                importProgress = 0
                successCount = 0
                skippedCount = 0
                
                importJob = coroutineScope.launch {
                    try {
                        // 在后台处理文件
                        withContext(Dispatchers.IO) {
                            uris.forEach { selectedUri ->
                                if (!isActive) return@withContext // 检查任务是否仍然活跃
                                val musicFile = importMusicFile(context, selectedUri)
                                if (musicFile != null) {
                                    // 检查此文件是否已导入（通过比较路径或唯一标识符）
                                    val isDuplicate = importedMusicFiles.any { existingFile ->
                                        existingFile.uri.toString() == musicFile.uri.toString() ||
                                        (existingFile.title == musicFile.title && existingFile.artist == musicFile.artist)
                                    }
                                    
                                    if (!isDuplicate) {
                                        withContext(Dispatchers.Main) {
                                            onMusicImported(musicFile)
                                            successCount++
                                            currentSessionImports++
                                        }
                                    } else {
                                        skippedCount++
                                    }
                                }
                                importProgress++
                            }
                        }
                        
                        // 显示导入结果（包含跳过的数量）
                        val message = if (skippedCount > 0) {
                            "已导入 $successCount 个文件。跳过 $skippedCount 个重复文件。"
                        } else {
                            "成功导入 $successCount 个文件。"
                        }
                        snackbarHostState.showSnackbar(message)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(
                            message = "导入文件时出错: ${e.message}"
                        )
                    } finally {
                        isImporting = false
                        importJob = null
                    }
                }
            }
        }
    }
    
    // 启动带有明确EXTRA_ALLOW_MULTIPLE标志的文件选择器的函数
    fun launchMusicPicker() {
        // 根据Android版本使用不同方法以获得更好的兼容性
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+使用ACTION_OPEN_DOCUMENT，它对多选有更好的支持
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                
                // 添加这些标志以确保文件保持可访问
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                
                // 在某些设备上，这有助于启用多选模式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildRootsUri(
                        "com.android.providers.media.documents"
                    ))
                }
            }
        } else {
            // 对于旧版本，回退到GET_CONTENT
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
        
        // 尝试使用此额外参数明确设置多选模式
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        
        // 创建选择器以确保用户能看到所有可用的文件选择器
        val chooserIntent = Intent.createChooser(intent, "选择音乐文件")
        musicFilePicker.launch(chooserIntent)
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
            // 音乐库卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "您的音乐库",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (importedMusicFiles.isEmpty()) 
                            "暂无音乐文件。导入一些音乐开始使用！" 
                        else 
                            "您的音乐库中有 ${importedMusicFiles.size} 首歌曲",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { navController.navigate("localMusic") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("浏览音乐库")
                    }
                }
            }
            
            // 导入音乐卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "导入音乐",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "选择多个音乐文件导入到您的音乐库",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        }
                    }
                    
                    // 显示当前会话导入数量（如果有）
                    if (currentSessionImports > 0 && !isImporting) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "您在本次会话中已导入 $currentSessionImports 个文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 在首次导入后显示多选帮助提示（如果之前未显示）
                    AnimatedVisibility(
                        visible = currentSessionImports == 1 && !hasShownMultiSelectHelp && !isImporting
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    
                                    Text(
                                        text = "提示：您可以在不关闭此界面的情况下导入多个文件。只需再次点击\"选择歌曲\"！",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    
                    // 在AnimatedVisibility外部添加LaunchedEffect
                    LaunchedEffect(currentSessionImports) {
                        if (currentSessionImports == 1) {
                            hasShownMultiSelectHelp = true
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 显示导入进度
                    if (isImporting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "正在导入 $totalFilesToImport 个文件中的第 $importProgress 个...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        if (successCount > 0) {
                            Text(
                                text = "已成功导入 $successCount 个" + 
                                if (skippedCount > 0) "，跳过 $skippedCount 个" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // 检查导入是否完成
                        if (importProgress >= totalFilesToImport) {
                            isImporting = false
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchMusicPicker() },
                            modifier = Modifier.weight(1f),
                            enabled = !isImporting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("选择歌曲")
                        }
                    }
                }
            }
        }
    }
} 