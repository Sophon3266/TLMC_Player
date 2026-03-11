package com.tlmc.player.ui.browser;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.tlmc.player.data.model.ServerConfig;
import com.tlmc.player.data.model.WebDavFile;
import com.tlmc.player.data.repository.ConfigManager;
import com.tlmc.player.data.repository.WebDavRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0010!\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0007\u0018\u00002\u00020\u0001B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0006\u0010\u001f\u001a\u00020 J\u0006\u0010!\u001a\u00020\"J\u000e\u0010#\u001a\u00020 2\u0006\u0010$\u001a\u00020\tJ\u0006\u0010%\u001a\u00020\u0011J\u0006\u0010&\u001a\u00020 J\u000e\u0010\'\u001a\u00020 2\u0006\u0010(\u001a\u00020\"R\u001c\u0010\u0007\u001a\u0010\u0012\f\u0012\n \n*\u0004\u0018\u00010\t0\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u000b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000e\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u000f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0010\u001a\u0010\u0012\f\u0012\n \n*\u0004\u0018\u00010\u00110\u00110\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\t0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u001d\u0010\u0016\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0015R\u0019\u0010\u0018\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u0015R\u001d\u0010\u001a\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u0015R\u0017\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00110\u0013\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0015R\u0014\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\t0\u001eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006)"}, d2 = {"Lcom/tlmc/player/ui/browser/BrowserViewModel;", "Landroidx/lifecycle/ViewModel;", "repository", "Lcom/tlmc/player/data/repository/WebDavRepository;", "configManager", "Lcom/tlmc/player/data/repository/ConfigManager;", "(Lcom/tlmc/player/data/repository/WebDavRepository;Lcom/tlmc/player/data/repository/ConfigManager;)V", "_currentPath", "Landroidx/lifecycle/MutableLiveData;", "", "kotlin.jvm.PlatformType", "_directoryFiles", "", "Lcom/tlmc/player/data/model/WebDavFile;", "_error", "_files", "_isLoading", "", "currentPath", "Landroidx/lifecycle/LiveData;", "getCurrentPath", "()Landroidx/lifecycle/LiveData;", "directoryFiles", "getDirectoryFiles", "error", "getError", "files", "getFiles", "isLoading", "pathHistory", "", "clearError", "", "getConfig", "Lcom/tlmc/player/data/model/ServerConfig;", "loadDirectory", "path", "navigateUp", "refresh", "saveConfig", "config", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class BrowserViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.tlmc.player.data.repository.WebDavRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.tlmc.player.data.repository.ConfigManager configManager = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> _files = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> files = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> _directoryFiles = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> directoryFiles = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.Boolean> _isLoading = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.Boolean> isLoading = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.String> _currentPath = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.String> currentPath = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.String> _error = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.String> error = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.String> pathHistory = null;
    
    @javax.inject.Inject()
    public BrowserViewModel(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.repository.WebDavRepository repository, @org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.repository.ConfigManager configManager) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> getFiles() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> getDirectoryFiles() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.Boolean> isLoading() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.String> getCurrentPath() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.String> getError() {
        return null;
    }
    
    public final void loadDirectory(@org.jetbrains.annotations.NotNull()
    java.lang.String path) {
    }
    
    public final void refresh() {
    }
    
    public final boolean navigateUp() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.tlmc.player.data.model.ServerConfig getConfig() {
        return null;
    }
    
    public final void saveConfig(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.model.ServerConfig config) {
    }
    
    public final void clearError() {
    }
}