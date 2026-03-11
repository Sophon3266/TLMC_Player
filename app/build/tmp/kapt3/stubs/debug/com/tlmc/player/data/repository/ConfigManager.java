package com.tlmc.player.data.repository;

import android.content.SharedPreferences;
import com.tlmc.player.data.model.ServerConfig;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\b\u0007\u0018\u0000 \f2\u00020\u0001:\u0001\fB\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\u0005\u001a\u00020\u0006J\u0006\u0010\u0007\u001a\u00020\bJ\u000e\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\u0006R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\r"}, d2 = {"Lcom/tlmc/player/data/repository/ConfigManager;", "", "prefs", "Landroid/content/SharedPreferences;", "(Landroid/content/SharedPreferences;)V", "getConfig", "Lcom/tlmc/player/data/model/ServerConfig;", "isConfigured", "", "saveConfig", "", "config", "Companion", "app_debug"})
public final class ConfigManager {
    @org.jetbrains.annotations.NotNull()
    private final android.content.SharedPreferences prefs = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_SERVER_URL = "server_url";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_USERNAME = "username";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_PASSWORD = "password";
    @org.jetbrains.annotations.NotNull()
    public static final com.tlmc.player.data.repository.ConfigManager.Companion Companion = null;
    
    @javax.inject.Inject()
    public ConfigManager(@org.jetbrains.annotations.NotNull()
    android.content.SharedPreferences prefs) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.tlmc.player.data.model.ServerConfig getConfig() {
        return null;
    }
    
    public final void saveConfig(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.model.ServerConfig config) {
    }
    
    public final boolean isConfigured() {
        return false;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/tlmc/player/data/repository/ConfigManager$Companion;", "", "()V", "KEY_PASSWORD", "", "KEY_SERVER_URL", "KEY_USERNAME", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}