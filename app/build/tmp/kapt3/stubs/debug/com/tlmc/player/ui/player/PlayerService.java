package com.tlmc.player.ui.player;

import android.content.Intent;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.tlmc.player.data.repository.ConfigManager;
import com.tlmc.player.data.model.ServerConfig;
import dagger.hilt.android.AndroidEntryPoint;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0011\u001a\u00020\u0012H\u0017J\b\u0010\u0013\u001a\u00020\u0012H\u0016J\u0012\u0010\u0014\u001a\u0004\u0018\u00010\n2\u0006\u0010\u0015\u001a\u00020\u0016H\u0016J\u0012\u0010\u0017\u001a\u00020\u00122\b\u0010\u0018\u001a\u0004\u0018\u00010\u0019H\u0016R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u000b\u001a\u00020\f8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u000e\"\u0004\b\u000f\u0010\u0010\u00a8\u0006\u001a"}, d2 = {"Lcom/tlmc/player/ui/player/PlayerService;", "Landroidx/media3/session/MediaSessionService;", "()V", "configManager", "Lcom/tlmc/player/data/repository/ConfigManager;", "getConfigManager", "()Lcom/tlmc/player/data/repository/ConfigManager;", "setConfigManager", "(Lcom/tlmc/player/data/repository/ConfigManager;)V", "mediaSession", "Landroidx/media3/session/MediaSession;", "okHttpClient", "Lokhttp3/OkHttpClient;", "getOkHttpClient", "()Lokhttp3/OkHttpClient;", "setOkHttpClient", "(Lokhttp3/OkHttpClient;)V", "onCreate", "", "onDestroy", "onGetSession", "controllerInfo", "Landroidx/media3/session/MediaSession$ControllerInfo;", "onTaskRemoved", "rootIntent", "Landroid/content/Intent;", "app_debug"})
public final class PlayerService extends androidx.media3.session.MediaSessionService {
    @javax.inject.Inject()
    public okhttp3.OkHttpClient okHttpClient;
    @javax.inject.Inject()
    public com.tlmc.player.data.repository.ConfigManager configManager;
    @org.jetbrains.annotations.Nullable()
    private androidx.media3.session.MediaSession mediaSession;
    
    public PlayerService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final okhttp3.OkHttpClient getOkHttpClient() {
        return null;
    }
    
    public final void setOkHttpClient(@org.jetbrains.annotations.NotNull()
    okhttp3.OkHttpClient p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.tlmc.player.data.repository.ConfigManager getConfigManager() {
        return null;
    }
    
    public final void setConfigManager(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.repository.ConfigManager p0) {
    }
    
    @java.lang.Override()
    @androidx.annotation.OptIn(markerClass = {androidx.media3.common.util.UnstableApi.class})
    public void onCreate() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public androidx.media3.session.MediaSession onGetSession(@org.jetbrains.annotations.NotNull()
    androidx.media3.session.MediaSession.ControllerInfo controllerInfo) {
        return null;
    }
    
    @java.lang.Override()
    public void onTaskRemoved(@org.jetbrains.annotations.Nullable()
    android.content.Intent rootIntent) {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
}