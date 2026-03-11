package com.tlmc.player.data.webdav;

import com.tlmc.player.data.model.WebDavFile;
import com.tlmc.player.data.repository.ConfigManager;
import kotlinx.coroutines.Dispatchers;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.StringReader;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0012\n\u0002\b\b\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u00002\u00020\u0001B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\nH\u0002J$\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\r2\u0006\u0010\u000b\u001a\u00020\nH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u000f\u0010\u0010J$\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\n0\r2\u0006\u0010\u000b\u001a\u00020\nH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0012\u0010\u0010J\b\u0010\u0013\u001a\u00020\nH\u0002J\u000e\u0010\u0014\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\nJ\u0006\u0010\u0015\u001a\u00020\u0003J*\u0010\u0016\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00180\u00170\r2\u0006\u0010\u000b\u001a\u00020\nH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0019\u0010\u0010J\u0010\u0010\u001a\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\nH\u0002J\u001e\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00180\u00172\u0006\u0010\u001c\u001a\u00020\n2\u0006\u0010\u001d\u001a\u00020\nH\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u001e"}, d2 = {"Lcom/tlmc/player/data/webdav/WebDavClient;", "", "okHttpClient", "Lokhttp3/OkHttpClient;", "configManager", "Lcom/tlmc/player/data/repository/ConfigManager;", "(Lokhttp3/OkHttpClient;Lcom/tlmc/player/data/repository/ConfigManager;)V", "dateFormat", "Ljava/text/SimpleDateFormat;", "buildUrl", "", "path", "downloadFile", "Lkotlin/Result;", "", "downloadFile-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "downloadFileAsString", "downloadFileAsString-gIAlu-s", "getAuthHeader", "getAuthenticatedUrl", "getOkHttpClientWithAuth", "listFiles", "", "Lcom/tlmc/player/data/model/WebDavFile;", "listFiles-gIAlu-s", "normalizePath", "parseMultiStatus", "xml", "currentPath", "app_debug"})
public final class WebDavClient {
    @org.jetbrains.annotations.NotNull()
    private final okhttp3.OkHttpClient okHttpClient = null;
    @org.jetbrains.annotations.NotNull()
    private final com.tlmc.player.data.repository.ConfigManager configManager = null;
    @org.jetbrains.annotations.NotNull()
    private final java.text.SimpleDateFormat dateFormat = null;
    
    @javax.inject.Inject()
    public WebDavClient(@org.jetbrains.annotations.NotNull()
    okhttp3.OkHttpClient okHttpClient, @org.jetbrains.annotations.NotNull()
    com.tlmc.player.data.repository.ConfigManager configManager) {
        super();
    }
    
    private final java.lang.String getAuthHeader() {
        return null;
    }
    
    private final java.lang.String buildUrl(java.lang.String path) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getAuthenticatedUrl(@org.jetbrains.annotations.NotNull()
    java.lang.String path) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final okhttp3.OkHttpClient getOkHttpClientWithAuth() {
        return null;
    }
    
    private final java.util.List<com.tlmc.player.data.model.WebDavFile> parseMultiStatus(java.lang.String xml, java.lang.String currentPath) {
        return null;
    }
    
    private final java.lang.String normalizePath(java.lang.String path) {
        return null;
    }
}