package com.example.myapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.myapp.data.AppRepository
import com.example.myapp.music.MusicPlayerManager

/**
 * 提供应用范围的依赖项并管理状态持久化
 */
object AppModule {

    /**
     * 记住MusicPlayerManager的单例实例，确保它在导航和配置更改中保持不变
     */
    @Composable
    fun provideMusicPlayerManager(context: Context): MusicPlayerManager {
        return remember { MusicPlayerManager(context) }
    }

    /**
     * 记住AppRepository的单例实例，确保它在导航和配置更改中保持不变
     */
    @Composable
    fun provideAppRepository(context: Context): AppRepository {
        return remember { AppRepository(context) }
    }
} 