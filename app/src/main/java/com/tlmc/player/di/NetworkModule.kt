package com.tlmc.player.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.common.util.UnstableApi
import com.tlmc.player.data.repository.ConfigManager
import com.tlmc.player.data.webdav.WebDavClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * 播放媒体磁盘缓存：CUE 分轨切换会重复打开同一个音频文件，
     * 通过共享缓存让已播放区间的数据无需再次下载。
     */
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideMediaCache(@ApplicationContext context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache")
        return try {
            SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_MAX_BYTES),
                StandaloneDatabaseProvider(context)
            )
        } catch (_: Exception) {
            // 缓存损坏时重建，避免影响播放
            cacheDir.deleteRecursively()
            SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_MAX_BYTES),
                StandaloneDatabaseProvider(context)
            )
        }
    }

    private const val MEDIA_CACHE_MAX_BYTES = 512L * 1024 * 1024

    @Provides
    @Singleton
    fun provideWebDavClient(
        okHttpClient: OkHttpClient,
        configManager: ConfigManager
    ): WebDavClient {
        return WebDavClient(okHttpClient, configManager)
    }
}

