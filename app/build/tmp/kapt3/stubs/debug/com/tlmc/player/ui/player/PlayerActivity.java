package com.tlmc.player.ui.player;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.tlmc.player.R;
import com.tlmc.player.data.model.PlayMode;
import com.tlmc.player.databinding.ActivityPlayerBinding;
import com.tlmc.player.ui.browser.BrowserPlaylistAdapter;
import com.tlmc.player.ui.browser.PlaylistItem;
import com.tlmc.player.util.LrcParser;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u008a\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0010\b\u0007\u0018\u0000 D2\u00020\u0001:\u0001DB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u001e\u001a\u00020\u001fH\u0002J\b\u0010 \u001a\u00020\u001fH\u0002J\u0010\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$H\u0002J\b\u0010%\u001a\u00020\u001fH\u0002J\b\u0010&\u001a\u00020\u001fH\u0002J\u0012\u0010\'\u001a\u00020\u001f2\b\u0010(\u001a\u0004\u0018\u00010)H\u0014J\b\u0010*\u001a\u00020\u001fH\u0014J\b\u0010+\u001a\u00020\u001fH\u0014J \u0010,\u001a\u00020\u001f2\u0006\u0010-\u001a\u00020\u00072\u0006\u0010.\u001a\u00020/2\u0006\u00100\u001a\u000201H\u0002J\u0016\u00102\u001a\u00020\u001f2\f\u00103\u001a\b\u0012\u0004\u0012\u00020504H\u0002J\b\u00106\u001a\u00020\u001fH\u0002J\b\u00107\u001a\u00020\u001fH\u0002J\b\u00108\u001a\u00020\u001fH\u0002J\b\u00109\u001a\u00020\u001fH\u0002J\b\u0010:\u001a\u00020\u001fH\u0002J\b\u0010;\u001a\u00020\u001fH\u0002J\u0010\u0010<\u001a\u00020\u001f2\u0006\u0010=\u001a\u00020$H\u0002J\b\u0010>\u001a\u00020\u001fH\u0002J\u0010\u0010?\u001a\u00020\u001f2\u0006\u0010@\u001a\u00020\u000fH\u0002J\u0010\u0010A\u001a\u00020\u001f2\u0006\u0010B\u001a\u00020\tH\u0002J\b\u0010C\u001a\u00020\u001fH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0005\u001a\n\u0012\u0004\u0012\u00020\u0007\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0017X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0018\u001a\u00020\u00198BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001c\u0010\u001d\u001a\u0004\b\u001a\u0010\u001b\u00a8\u0006E"}, d2 = {"Lcom/tlmc/player/ui/player/PlayerActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "binding", "Lcom/tlmc/player/databinding/ActivityPlayerBinding;", "controllerFuture", "Lcom/google/common/util/concurrent/ListenableFuture;", "Landroidx/media3/session/MediaController;", "currentLyricsLineIndex", "", "currentPlayMode", "Lcom/tlmc/player/data/model/PlayMode;", "handler", "Landroid/os/Handler;", "isCueMode", "", "isSeeking", "lyricsAdapter", "Lcom/tlmc/player/ui/player/LyricsAdapter;", "mediaController", "trackAdapter", "Lcom/tlmc/player/ui/player/TrackAdapter;", "updateProgressRunnable", "Ljava/lang/Runnable;", "viewModel", "Lcom/tlmc/player/ui/player/PlayerViewModel;", "getViewModel", "()Lcom/tlmc/player/ui/player/PlayerViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "applyPlayModeToController", "", "connectToService", "formatTime", "", "ms", "", "observeViewModel", "onControllerConnected", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onStart", "onStop", "refreshPlaylistDialog", "controller", "recyclerView", "Landroidx/recyclerview/widget/RecyclerView;", "emptyView", "Landroid/widget/TextView;", "setMediaItems", "items", "", "Landroidx/media3/common/MediaItem;", "setupControls", "setupTrackList", "showPlaylistDialog", "syncPlayModeFromController", "togglePlayMode", "updateCurrentTrackInfo", "updateLyricsPosition", "positionMs", "updatePlayModeIcon", "updatePlayPauseButton", "isPlaying", "updatePlaybackState", "state", "updateProgress", "Companion", "app_debug"})
public final class PlayerActivity extends androidx.appcompat.app.AppCompatActivity {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_FILE_PATH = "extra_file_path";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_FILE_NAME = "extra_file_name";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_CUE_PATH = "extra_cue_path";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_DIRECTORY_PATH = "extra_directory_path";
    private com.tlmc.player.databinding.ActivityPlayerBinding binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    @org.jetbrains.annotations.Nullable()
    private com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController> controllerFuture;
    @org.jetbrains.annotations.Nullable()
    private androidx.media3.session.MediaController mediaController;
    private com.tlmc.player.ui.player.TrackAdapter trackAdapter;
    private com.tlmc.player.ui.player.LyricsAdapter lyricsAdapter;
    private int currentLyricsLineIndex = -1;
    @org.jetbrains.annotations.NotNull()
    private com.tlmc.player.data.model.PlayMode currentPlayMode = com.tlmc.player.data.model.PlayMode.SEQUENTIAL;
    private boolean isSeeking = false;
    private boolean isCueMode = false;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable updateProgressRunnable = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.tlmc.player.ui.player.PlayerActivity.Companion Companion = null;
    
    public PlayerActivity() {
        super();
    }
    
    private final com.tlmc.player.ui.player.PlayerViewModel getViewModel() {
        return null;
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override()
    protected void onStart() {
    }
    
    @java.lang.Override()
    protected void onStop() {
    }
    
    private final void connectToService() {
    }
    
    private final void onControllerConnected() {
    }
    
    private final void setupTrackList() {
    }
    
    private final void setupControls() {
    }
    
    private final void observeViewModel() {
    }
    
    private final void setMediaItems(java.util.List<androidx.media3.common.MediaItem> items) {
    }
    
    private final void updateProgress() {
    }
    
    private final void updatePlayPauseButton(boolean isPlaying) {
    }
    
    private final void updateCurrentTrackInfo() {
    }
    
    private final void updatePlaybackState(int state) {
    }
    
    private final void togglePlayMode() {
    }
    
    private final void applyPlayModeToController() {
    }
    
    private final void syncPlayModeFromController() {
    }
    
    private final void updatePlayModeIcon() {
    }
    
    private final void showPlaylistDialog() {
    }
    
    private final void refreshPlaylistDialog(androidx.media3.session.MediaController controller, androidx.recyclerview.widget.RecyclerView recyclerView, android.widget.TextView emptyView) {
    }
    
    private final void updateLyricsPosition(long positionMs) {
    }
    
    private final java.lang.String formatTime(long ms) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\b"}, d2 = {"Lcom/tlmc/player/ui/player/PlayerActivity$Companion;", "", "()V", "EXTRA_CUE_PATH", "", "EXTRA_DIRECTORY_PATH", "EXTRA_FILE_NAME", "EXTRA_FILE_PATH", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}