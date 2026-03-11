package com.tlmc.player.di

import com.tlmc.player.data.repository.ConfigManager
import com.tlmc.player.data.webdav.WebDavClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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

    @Provides
    @Singleton
    fun provideWebDavClient(
        okHttpClient: OkHttpClient,
        configManager: ConfigManager
    ): WebDavClient {
        return WebDavClient(okHttpClient, configManager)
    }
}

