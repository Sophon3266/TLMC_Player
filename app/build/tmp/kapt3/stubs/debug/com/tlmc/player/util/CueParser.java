package com.tlmc.player.util;

import com.tlmc.player.data.model.CueSheet;
import com.tlmc.player.data.model.CueTrack;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0004H\u0002J\u0010\u0010\u0006\u001a\u00020\u00072\u0006\u0010\u0005\u001a\u00020\u0004H\u0002J\u000e\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u0004J\u0010\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0004H\u0002\u00a8\u0006\u000e"}, d2 = {"Lcom/tlmc/player/util/CueParser;", "", "()V", "extractQuotedValue", "", "line", "extractTrackNumber", "", "parse", "Lcom/tlmc/player/data/model/CueSheet;", "content", "parseTimestamp", "", "timestamp", "app_debug"})
public final class CueParser {
    @org.jetbrains.annotations.NotNull()
    public static final com.tlmc.player.util.CueParser INSTANCE = null;
    
    private CueParser() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.tlmc.player.data.model.CueSheet parse(@org.jetbrains.annotations.NotNull()
    java.lang.String content) {
        return null;
    }
    
    private final java.lang.String extractQuotedValue(java.lang.String line) {
        return null;
    }
    
    private final int extractTrackNumber(java.lang.String line) {
        return 0;
    }
    
    /**
     * Parse CUE timestamp in MM:SS:FF format (FF = frames, 75 frames per second)
     * Returns time in milliseconds
     */
    private final long parseTimestamp(java.lang.String timestamp) {
        return 0L;
    }
}