package com.tlmc.player.ui.player;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import com.tlmc.player.data.model.CueSheet;
import com.tlmc.player.data.model.CueTrack;
import com.tlmc.player.data.model.LrcLine;
import com.tlmc.player.data.repository.WebDavRepository;
import com.tlmc.player.util.FileUtils;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0010\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\r\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010$\u001a\b\u0012\u0004\u0012\u00020\u00110\u000e2\u0006\u0010%\u001a\u00020&2\u0006\u0010\'\u001a\u00020\u0007H\u0002J\u0006\u0010(\u001a\u00020)J\"\u0010*\u001a\u00020)2\b\u0010+\u001a\u0004\u0018\u00010\u00072\b\u0010,\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u001a\u001a\u00020\u0007J\u001a\u0010-\u001a\u00020)2\u0006\u0010.\u001a\u00020\u00072\n\b\u0002\u0010/\u001a\u0004\u0018\u00010\u0007J\u0010\u00100\u001a\u00020)2\b\u00101\u001a\u0004\u0018\u00010\u0011J\u0010\u00102\u001a\u00020)2\u0006\u0010+\u001a\u00020\u0007H\u0002J(\u00103\u001a\u00020)2\u0006\u0010,\u001a\u00020\u00072\b\u00104\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u001a\u001a\u00020\u0007H\u0082@\u00a2\u0006\u0002\u00105R\u001c\u0010\u0005\u001a\u0010\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\t\u001a\u0010\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u000b\u001a\u0010\u0012\f\u0012\n \b*\u0004\u0018\u00010\f0\f0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\r\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0010\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00110\u000e0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0012\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00130\u000e0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00070\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0017\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00070\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u0017R\u000e\u0010\u001a\u001a\u00020\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u001b\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0017R\u0017\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\f0\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u0017R\u001d\u0010\u001e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u000e0\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u0017R\u001d\u0010 \u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00110\u000e0\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u0017R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\"\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00130\u000e0\u0015\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010\u0017\u00a8\u00066"}, d2 = {"Lcom/tlmc/player/ui/player/PlayerViewModel;", "Landroidx/lifecycle/ViewModel;", "repository", "Lcom/tlmc/player/data/repository/WebDavRepository;", "(Lcom/tlmc/player/data/repository/WebDavRepository;)V", "_albumArtist", "Landroidx/lifecycle/MutableLiveData;", "", "kotlin.jvm.PlatformType", "_albumTitle", "_error", "_isLoading", "", "_lyricsLines", "", "Lcom/tlmc/player/data/model/LrcLine;", "_mediaItems", "Landroidx/media3/common/MediaItem;", "_tracks", "Lcom/tlmc/player/data/model/CueTrack;", "albumArtist", "Landroidx/lifecycle/LiveData;", "getAlbumArtist", "()Landroidx/lifecycle/LiveData;", "albumTitle", "getAlbumTitle", "directoryPath", "error", "getError", "isLoading", "lyricsLines", "getLyricsLines", "mediaItems", "getMediaItems", "tracks", "getTracks", "buildCueMediaItems", "cueSheet", "Lcom/tlmc/player/data/model/CueSheet;", "audioUrl", "clearLyrics", "", "initialize", "filePath", "cuePath", "loadLyricsForTrack", "audioFileName", "dirPath", "loadLyricsFromMediaItem", "mediaItem", "loadSingleFile", "loadWithCue", "audioFilePath", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class PlayerViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.tlmc.player.data.repository.WebDavRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.util.List<com.tlmc.player.data.model.CueTrack>> _tracks = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.CueTrack>> tracks = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.util.List<androidx.media3.common.MediaItem>> _mediaItems = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.util.List<androidx.media3.common.MediaItem>> mediaItems = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.String> _albumTitle = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.String> albumTitle = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.String> _albumArtist = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.String> albumArtist = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.Boolean> _isLoading = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.Boolean> isLoading = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.lang.String> _error = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.lang.String> error = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<java.util.List<com.tlmc.player.data.model.LrcLine>> _lyricsLines = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.LrcLine>> lyricsLines = null;
    @org.jetbrains.annotations.NotNull()
    private java.lang.String directoryPath = "/";
    
    @javax.inject.Inject()
    public PlayerViewModel(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.repository.WebDavRepository repository) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.CueTrack>> getTracks() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<androidx.media3.common.MediaItem>> getMediaItems() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.String> getAlbumTitle() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.String> getAlbumArtist() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.Boolean> isLoading() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.lang.String> getError() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.tlmc.player.data.model.LrcLine>> getLyricsLines() {
        return null;
    }
    
    public final void initialize(@org.jetbrains.annotations.Nullable()
    java.lang.String filePath, @org.jetbrains.annotations.Nullable()
    java.lang.String cuePath, @org.jetbrains.annotations.NotNull()
    java.lang.String directoryPath) {
    }
    
    private final java.lang.Object loadWithCue(java.lang.String cuePath, java.lang.String audioFilePath, java.lang.String directoryPath, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final void loadSingleFile(java.lang.String filePath) {
    }
    
    private final java.util.List<androidx.media3.common.MediaItem> buildCueMediaItems(com.tlmc.player.data.model.CueSheet cueSheet, java.lang.String audioUrl) {
        return null;
    }
    
    /**
     * Load lyrics for the given audio file name.
     * Looks for a .lrc file with the same base name in the given directory.
     */
    public final void loadLyricsForTrack(@org.jetbrains.annotations.NotNull()
    java.lang.String audioFileName, @org.jetbrains.annotations.Nullable()
    java.lang.String dirPath) {
    }
    
    /**
     * Load lyrics for the current media item.
     * Uses mediaId (original WebDAV path) to find the matching .lrc file.
     * Falls back to directoryPath + title if mediaId is not available.
     */
    public final void loadLyricsFromMediaItem(@org.jetbrains.annotations.Nullable()
    androidx.media3.common.MediaItem mediaItem) {
    }
    
    public final void clearLyrics() {
    }
}