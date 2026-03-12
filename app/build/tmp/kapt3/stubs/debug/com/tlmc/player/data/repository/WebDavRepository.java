package com.tlmc.player.data.repository;

import com.tlmc.player.data.model.CueSheet;
import com.tlmc.player.data.model.LrcLine;
import com.tlmc.player.data.model.WebDavFile;
import com.tlmc.player.data.webdav.WebDavClient;
import com.tlmc.player.util.CueParser;
import com.tlmc.player.util.EncodingDetector;
import com.tlmc.player.util.LrcParser;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J$\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\b\u001a\u00020\tH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\n\u0010\u000bJ$\u0010\f\u001a\b\u0012\u0004\u0012\u00020\t0\u00062\u0006\u0010\b\u001a\u00020\tH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\r\u0010\u000bJ&\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\u0010\u001a\u00020\u000f2\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0012H\u0086@\u00a2\u0006\u0002\u0010\u0013J\u001e\u0010\u0014\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\u0015\u001a\u00020\t2\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0012J \u0010\u0016\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0015\u001a\u00020\t2\u0006\u0010\u0017\u001a\u00020\tH\u0086@\u00a2\u0006\u0002\u0010\u0018J\u0006\u0010\u0019\u001a\u00020\u001aJ\u000e\u0010\u001b\u001a\u00020\t2\u0006\u0010\b\u001a\u00020\tJ*\u0010\u001c\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000f0\u00120\u00062\u0006\u0010\b\u001a\u00020\tH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u001d\u0010\u000bJ$\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u001f0\u00062\u0006\u0010 \u001a\u00020\tH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b!\u0010\u000bJ*\u0010\"\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020#0\u00120\u00062\u0006\u0010$\u001a\u00020\tH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b%\u0010\u000bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006&"}, d2 = {"Lcom/tlmc/player/data/repository/WebDavRepository;", "", "webDavClient", "Lcom/tlmc/player/data/webdav/WebDavClient;", "(Lcom/tlmc/player/data/webdav/WebDavClient;)V", "downloadFile", "Lkotlin/Result;", "", "path", "", "downloadFile-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "downloadTextFile", "downloadTextFile-gIAlu-s", "findMatchingCue", "Lcom/tlmc/player/data/model/WebDavFile;", "audioFile", "directoryFiles", "", "(Lcom/tlmc/player/data/model/WebDavFile;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "findMatchingLrcInList", "audioFileName", "findMatchingLrcPath", "directoryPath", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAuthenticatedOkHttpClient", "Lokhttp3/OkHttpClient;", "getFileUrl", "listFiles", "listFiles-gIAlu-s", "loadCueSheet", "Lcom/tlmc/player/data/model/CueSheet;", "cuePath", "loadCueSheet-gIAlu-s", "loadLrcFile", "Lcom/tlmc/player/data/model/LrcLine;", "lrcPath", "loadLrcFile-gIAlu-s", "app_debug"})
public final class WebDavRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.tlmc.player.data.webdav.WebDavClient webDavClient = null;
    
    @javax.inject.Inject()
    public WebDavRepository(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.webdav.WebDavClient webDavClient) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getFileUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String path) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final okhttp3.OkHttpClient getAuthenticatedOkHttpClient() {
        return null;
    }
    
    /**
     * Find a CUE file matching the audio file name in the same directory
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object findMatchingCue(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.model.WebDavFile audioFile, @org.jetbrains.annotations.NotNull()
    java.util.List<com.tlmc.player.data.model.WebDavFile> directoryFiles, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.tlmc.player.data.model.WebDavFile> $completion) {
        return null;
    }
    
    /**
     * Try to find an LRC file matching the audio file name in a directory.
     * Returns the LRC path if found, null otherwise.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object findMatchingLrcPath(@org.jetbrains.annotations.NotNull()
    java.lang.String audioFileName, @org.jetbrains.annotations.NotNull()
    java.lang.String directoryPath, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    /**
     * Try to find an LRC file matching the audio file name in directory file list.
     */
    @org.jetbrains.annotations.Nullable()
    public final com.tlmc.player.data.model.WebDavFile findMatchingLrcInList(@org.jetbrains.annotations.NotNull()
    java.lang.String audioFileName, @org.jetbrains.annotations.NotNull()
    java.util.List<com.tlmc.player.data.model.WebDavFile> directoryFiles) {
        return null;
    }
}