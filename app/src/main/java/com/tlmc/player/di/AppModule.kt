package com.tlmc.player.di

import android.content.Context
import android.content.SharedPreferences
import com.tlmc.player.data.repository.ConfigManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("tlmc_player_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideConfigManager(prefs: SharedPreferences): ConfigManager {
        return ConfigManager(prefs)
    }
}

