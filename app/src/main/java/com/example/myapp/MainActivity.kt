package com.example.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapp.ui.ai.AIScreen
import com.example.myapp.ui.music.MusicScreen
import com.example.myapp.ui.settings.SettingsScreen
import com.example.myapp.ui.theme.MyAPPTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAPPTheme {
                MusicPlayerApp()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Music : Screen("music", "Music", Icons.Filled.MusicNote)
    data object AI : Screen("ai", "AI", Icons.Filled.SmartToy)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}
val items = listOf(
    Screen.Music,
    Screen.AI,
    Screen.Settings
)

@Composable
fun MusicPlayerApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 创建在屏幕之间共享的持久实例
    val musicPlayerManager = AppModule.provideMusicPlayerManager(context)
    val repository = AppModule.provideAppRepository(context)
    
    // 观察当前歌曲信息
    val currentSongTitle by musicPlayerManager.currentSongTitle.collectAsState()
    val isPlaying by musicPlayerManager.isPlaying.collectAsState()
    
    // 处理音乐播放器的生命周期事件
    val currentMusicPlayerManager = rememberUpdatedState(musicPlayerManager)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // 当应用进入后台时暂停播放
                    if (currentMusicPlayerManager.value.isPlaying.value) {
                        currentMusicPlayerManager.value.playPause()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // 当应用被销毁时释放资源
                    currentMusicPlayerManager.value.release()
                }
                else -> { /* 忽略其他事件 */ }
            }
        }
        
        // 将观察者添加到生命周期
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // 当效果离开组合时，移除观察者
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            // 导航内容
            NavHost(navController, startDestination = Screen.Music.route) {
                composable(Screen.Music.route) { 
                    MusicScreen(
                        musicPlayerManager = musicPlayerManager,
                        repository = repository
                    )
                }
                composable(Screen.AI.route) { AIScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}