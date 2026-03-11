package com.tlmc.player.data.repository

import android.content.SharedPreferences
import com.tlmc.player.data.model.ServerConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManager @Inject constructor(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }

    fun getConfig(): ServerConfig {
        return ServerConfig(
            url = prefs.getString(KEY_SERVER_URL, ServerConfig().url) ?: ServerConfig().url,
            username = prefs.getString(KEY_USERNAME, ServerConfig().username) ?: ServerConfig().username,
            password = prefs.getString(KEY_PASSWORD, ServerConfig().password) ?: ServerConfig().password
        )
    }

    fun saveConfig(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.url)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .apply()
    }

    fun isConfigured(): Boolean {
        return prefs.contains(KEY_SERVER_URL)
    }
}

