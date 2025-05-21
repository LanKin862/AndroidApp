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
    
    // Create persistent instances that will be shared across screens
    val musicPlayerManager = AppModule.provideMusicPlayerManager(context)
    val repository = AppModule.provideAppRepository(context)
    
    // Observe current song information
    val currentSongTitle by musicPlayerManager.currentSongTitle.collectAsState()
    val isPlaying by musicPlayerManager.isPlaying.collectAsState()
    
    // Handle lifecycle events for the music player
    val currentMusicPlayerManager = rememberUpdatedState(musicPlayerManager)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Pause playback when app goes to background
                    if (currentMusicPlayerManager.value.isPlaying.value) {
                        currentMusicPlayerManager.value.playPause()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // Release resources when app is destroyed
                    currentMusicPlayerManager.value.release()
                }
                else -> { /* Ignore other events */ }
            }
        }
        
        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // When the effect leaves the Composition, remove the observer
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
            // Navigation Content
            NavHost(navController, startDestination = Screen.Music.route) {
                composable(Screen.Music.route) { 
                    MusicScreen(
                        musicPlayerManager = musicPlayerManager,
                        repository = repository
                    )
                }
//                composable(Screen.AI.route) { AIScreen() }
//                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}