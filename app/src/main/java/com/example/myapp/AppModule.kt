package com.example.myapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.myapp.data.AppRepository
import com.example.myapp.music.MusicPlayerManager

/**
 * Provides application-wide dependencies and manages state persistence
 */
object AppModule {

    /**
     * Remember a singleton instance of MusicPlayerManager to ensure it persists across navigation and configuration changes
     */
    @Composable
    fun provideMusicPlayerManager(context: Context): MusicPlayerManager {
        return remember { MusicPlayerManager(context) }
    }

    /**
     * Remember a singleton instance of AppRepository to ensure it persists across navigation and configuration changes
     */
    @Composable
    fun provideAppRepository(context: Context): AppRepository {
        return remember { AppRepository(context) }
    }
} 