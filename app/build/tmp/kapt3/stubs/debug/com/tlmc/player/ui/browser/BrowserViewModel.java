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

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0010!\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u0000 <2\u00020\u0001:\u0001<B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0006\u0010)\u001a\u00020*J\u0006\u0010+\u001a\u00020*J\u0006\u0010,\u001a\u00020-J\u000e\u0010.\u001a\u00020\t2\u0006\u0010/\u001a\u00020\tJ\u000e\u00100\u001a\u00020*2\u0006\u0010/\u001a\u00020\tJ\u0006\u00101\u001a\u00020\u0011J\u0006\u00102\u001a\u00020*J\u000e\u00103\u001a\u00020*2\u0006\u00104\u001a\u00020-J\u000e\u00105\u001a\u00020*2\u0006\u00106\u001a\u00020\tJ4\u00107\u001a\u00020*2\u0006\u0010/\u001a\u00020\t2\u0006\u00106\u001a\u00020\t2\f\u00108\u001a\b\u0012\u0004\u0012\u00020\r0\"2\u0006\u00109\u001a\u00020:H\u0082@\u00a2\u0006\u0002\u0010;R\u001c\u0010\u0007\u001a\u0010\u0012\f\u0012\n \n*\u0004\u0018\u00010\t0\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u000b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000e\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u000f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0010\u001a\u0010\u0012\f\u0012\n \n*\u0004\u0018\u00010\u00110\u00110\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0012\u001a\u0010\u0012\f\u0012\n \n*\u0004\u0018\u00010\u00110\u00110\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0013\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0014\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\t0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u001d\u0010\u0019\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0018R\u0019\u0010\u001b\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0018R\u001d\u0010\u001d\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0018R\u0017\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00110\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u0018R\u0017\u0010 \u001a\b\u0012\u0004\u0012\u00020\u00110\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0018R\u0014\u0010!\u001a\b\u0012\u0004\u0012\u00020\t0\"X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010#\u001a\u0004\u0018\u00010$X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001d\u0010%\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\r0\f0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\u0018R\u0019\u0010\'\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010\u0018\u00a8\u0006="}, d2 = {"Lcom/tlmc/player/ui/browser/BrowserViewModel;", "Landroidx/lifecycle/ViewModel;", "repository", "Lcom/tlmc/player/data/repository/WebDavRepository;", "configManager", "Lcom/tlmc/player/data/repository/ConfigManager;", "(Lcom/tlmc/player/data/repository/WebDavRepository;Lcom/tlmc/player/data/repository/ConfigManager;)V", "_currentPath", "Landroidx/lifecycle/MutableLiveData;", "", "kotlin.jvm.PlatformType", "_directoryFiles", "", "Lcom/tlmc/player/data/model/WebDavFile;", "_error", "_files", "_isLoading", "", "_isSearching", "_searchResults", "_searchStatus", "currentPath", "Landroidx/lifecycle/LiveData;", "getCurrentPath", "()Landroidx/lifecycle/LiveData;", "directoryFiles", "getDirectoryFiles", "error", "getError", "files", "getFiles", "isLoading", "isSearching", "pathHistory", "", "searchJob", "Lkotlinx/coroutines/Job;", "searchResults", "getSearchResults", "searchStatus", "getSearchStatus", "cancelSearch", "", "clearError", "getConfig", "Lcom/tlmc/player/data/model/ServerConfig;", "getFileUrl", "path", "loadDirectory", "navigateUp", "refresh", "saveConfig", "config", "searchFiles", "query", "searchRecursiveParallel", "results", "semaphore", "Lkotlinx/coroutines/sync/Semaphore;", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Lkotlinx/coroutines/sync/Semaphore;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "app_debug"})
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
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> _searchResults = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> searchResults = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.Boolean> _isSearching = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.Boolean> isSearching = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.String> _searchStatus = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.String> searchStatus = null;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job searchJob;
    private static final int MAX_SEARCH_RESULTS = 200;
    private static final int MAX_CONCURRENT_REQUESTS = 32;
    @org.jetbrains.annotations.NotNull()
    public static final com.tlmc.player.ui.browser.BrowserViewModel.Companion Companion = null;
    
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
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.WebDavFile>> getSearchResults() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.Boolean> isSearching() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.String> getSearchStatus() {
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
    
    public final void searchFiles(@org.jetbrains.annotations.NotNull()
    java.lang.String query) {
    }
    
    private final java.lang.Object searchRecursiveParallel(java.lang.String path, java.lang.String query, java.util.List<com.tlmc.player.data.model.WebDavFile> results, kotlinx.coroutines.sync.Semaphore semaphore, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void cancelSearch() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getFileUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String path) {
        return null;
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/tlmc/player/ui/browser/BrowserViewModel$Companion;", "", "()V", "MAX_CONCURRENT_REQUESTS", "", "MAX_SEARCH_RESULTS", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}