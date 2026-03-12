package com.tlmc.player.ui.browser;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.tlmc.player.R;
import com.tlmc.player.data.model.PlayMode;
import com.tlmc.player.data.model.ServerConfig;
import com.tlmc.player.data.model.WebDavFile;
import com.tlmc.player.databinding.ActivityBrowserBinding;
import com.tlmc.player.ui.image.ImageActivity;
import com.tlmc.player.ui.player.PlayerActivity;
import com.tlmc.player.ui.player.PlayerService;
import com.tlmc.player.ui.text.TextActivity;
import com.tlmc.player.util.FileUtils;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00a8\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u0007\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0010 \n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0014\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010$\u001a\u00020%2\u0006\u0010&\u001a\u00020\'2\u0006\u0010(\u001a\u00020\'H\u0002J\b\u0010)\u001a\u00020%H\u0002J\u0010\u0010*\u001a\u00020%2\u0006\u0010+\u001a\u00020,H\u0002J\b\u0010-\u001a\u00020%H\u0002J\u0010\u0010.\u001a\u00020\u00132\u0006\u0010+\u001a\u00020,H\u0002J\b\u0010/\u001a\u00020%H\u0002J\u0010\u00100\u001a\u00020%2\u0006\u0010+\u001a\u00020,H\u0002J\u0010\u00101\u001a\u00020%2\u0006\u00102\u001a\u000203H\u0002J\b\u00104\u001a\u00020%H\u0002J\b\u00105\u001a\u00020%H\u0017J\b\u00106\u001a\u00020%H\u0002J\u0012\u00107\u001a\u00020%2\b\u00108\u001a\u0004\u0018\u000109H\u0014J\u0010\u0010:\u001a\u00020\u000f2\u0006\u0010;\u001a\u00020<H\u0016J\u0010\u0010=\u001a\u00020%2\u0006\u0010+\u001a\u00020,H\u0002J\u0010\u0010>\u001a\u00020\u000f2\u0006\u0010+\u001a\u00020,H\u0002J\u0010\u0010?\u001a\u00020\u000f2\u0006\u0010@\u001a\u00020AH\u0016J\b\u0010B\u001a\u00020%H\u0014J\b\u0010C\u001a\u00020%H\u0014J\u0010\u0010D\u001a\u00020%2\u0006\u0010+\u001a\u00020,H\u0002J\u0010\u0010E\u001a\u00020%2\u0006\u0010F\u001a\u00020,H\u0002J\u0010\u0010G\u001a\u00020%2\u0006\u0010+\u001a\u00020,H\u0002J\u001e\u0010H\u001a\u00020%2\u0006\u0010I\u001a\u00020,2\f\u0010J\u001a\b\u0012\u0004\u0012\u00020,0KH\u0002J\u0010\u0010L\u001a\u00020%2\u0006\u0010+\u001a\u00020,H\u0002J \u0010M\u001a\u00020%2\u0006\u0010N\u001a\u00020\u00072\u0006\u0010O\u001a\u00020P2\u0006\u0010Q\u001a\u00020\u001bH\u0002J\b\u0010R\u001a\u00020%H\u0002J\b\u0010S\u001a\u00020%H\u0003J\b\u0010T\u001a\u00020%H\u0002J\b\u0010U\u001a\u00020%H\u0002J\b\u0010V\u001a\u00020%H\u0002J\u0010\u0010W\u001a\u00020%2\u0006\u0010+\u001a\u00020,H\u0002J\b\u0010X\u001a\u00020%H\u0002J\b\u0010Y\u001a\u00020%H\u0002J\b\u0010Z\u001a\u00020%H\u0002J\b\u0010[\u001a\u00020%H\u0002J\b\u0010\\\u001a\u00020%H\u0002J\u0010\u0010]\u001a\u00020%2\u0006\u0010(\u001a\u00020\'H\u0002J\b\u0010^\u001a\u00020%H\u0002J\b\u0010_\u001a\u00020%H\u0002J\b\u0010`\u001a\u00020%H\u0002J\b\u0010a\u001a\u00020%H\u0002J\b\u0010b\u001a\u00020%H\u0002J\b\u0010c\u001a\u00020%H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0005\u001a\n\u0012\u0004\u0012\u00020\u0007\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0007X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0012\u001a\u0004\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0015X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0018\u001a\u0004\u0018\u00010\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u001bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u001dX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u001e\u001a\u00020\u001f8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\"\u0010#\u001a\u0004\b \u0010!\u00a8\u0006d"}, d2 = {"Lcom/tlmc/player/ui/browser/BrowserActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "binding", "Lcom/tlmc/player/databinding/ActivityBrowserBinding;", "controllerFuture", "Lcom/google/common/util/concurrent/ListenableFuture;", "Landroidx/media3/session/MediaController;", "currentPlayMode", "Lcom/tlmc/player/data/model/PlayMode;", "fileAdapter", "Lcom/tlmc/player/ui/browser/FileAdapter;", "handler", "Landroid/os/Handler;", "isDraggingScrollbar", "", "isMiniPlayerSeeking", "mediaController", "pendingMediaItem", "Landroidx/media3/common/MediaItem;", "searchDialog", "Landroidx/appcompat/app/AlertDialog;", "searchProgressBar", "Landroid/widget/ProgressBar;", "searchResultAdapter", "Lcom/tlmc/player/ui/browser/SearchResultAdapter;", "searchStatusText", "Landroid/widget/TextView;", "updateProgressRunnable", "Ljava/lang/Runnable;", "viewModel", "Lcom/tlmc/player/ui/browser/BrowserViewModel;", "getViewModel", "()Lcom/tlmc/player/ui/browser/BrowserViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "addBreadcrumbChip", "", "label", "", "path", "addBreadcrumbSeparator", "addToPlaylist", "file", "Lcom/tlmc/player/data/model/WebDavFile;", "applyPlayModeToController", "buildMediaItemForFile", "connectToPlayerService", "handleAudioFileClick", "handleScrollbarDrag", "touchY", "", "observeViewModel", "onBackPressed", "onControllerConnected", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onCreateOptionsMenu", "menu", "Landroid/view/Menu;", "onFileClicked", "onFileLongClicked", "onOptionsItemSelected", "item", "Landroid/view/MenuItem;", "onStart", "onStop", "openImage", "openPlayerFromCue", "cueFile", "openText", "playFolderAudioFiles", "clickedFile", "audioFiles", "", "playSingleFile", "refreshPlaylistDialog", "controller", "recyclerView", "Landroidx/recyclerview/widget/RecyclerView;", "emptyView", "requestNotificationPermission", "setupFastScroller", "setupMiniPlayer", "setupRecyclerView", "setupSwipeRefresh", "showFileInfo", "showPlaylistDialog", "showSearchDialog", "showSettingsDialog", "syncPlayModeFromController", "togglePlayMode", "updateBreadcrumb", "updateFastScrollThumb", "updateMiniPlayer", "updateMiniPlayerControls", "updateMiniPlayerInfo", "updateMiniPlayerVisibility", "updatePlayModeIcon", "app_debug"})
public final class BrowserActivity extends androidx.appcompat.app.AppCompatActivity {
    private com.tlmc.player.databinding.ActivityBrowserBinding binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    private com.tlmc.player.ui.browser.FileAdapter fileAdapter;
    @org.jetbrains.annotations.Nullable()
    private com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController> controllerFuture;
    @org.jetbrains.annotations.Nullable()
    private androidx.media3.session.MediaController mediaController;
    @org.jetbrains.annotations.NotNull()
    private final android.os.Handler handler = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.Runnable updateProgressRunnable = null;
    @org.jetbrains.annotations.Nullable()
    private androidx.media3.common.MediaItem pendingMediaItem;
    @org.jetbrains.annotations.NotNull()
    private com.tlmc.player.data.model.PlayMode currentPlayMode = com.tlmc.player.data.model.PlayMode.SEQUENTIAL;
    private boolean isMiniPlayerSeeking = false;
    @org.jetbrains.annotations.Nullable()
    private androidx.appcompat.app.AlertDialog searchDialog;
    @org.jetbrains.annotations.Nullable()
    private com.tlmc.player.ui.browser.SearchResultAdapter searchResultAdapter;
    @org.jetbrains.annotations.Nullable()
    private android.widget.ProgressBar searchProgressBar;
    @org.jetbrains.annotations.Nullable()
    private android.widget.TextView searchStatusText;
    private boolean isDraggingScrollbar = false;
    
    public BrowserActivity() {
        super();
    }
    
    private final com.tlmc.player.ui.browser.BrowserViewModel getViewModel() {
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
    
    private final void requestNotificationPermission() {
    }
    
    private final void setupRecyclerView() {
    }
    
    @android.annotation.SuppressLint(value = {"ClickableViewAccessibility"})
    private final void setupFastScroller() {
    }
    
    private final void updateFastScrollThumb() {
    }
    
    private final void handleScrollbarDrag(float touchY) {
    }
    
    private final void setupSwipeRefresh() {
    }
    
    private final void observeViewModel() {
    }
    
    private final void updateBreadcrumb(java.lang.String path) {
    }
    
    private final void addBreadcrumbChip(java.lang.String label, java.lang.String path) {
    }
    
    private final void addBreadcrumbSeparator() {
    }
    
    private final void onFileClicked(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void handleAudioFileClick(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void playSingleFile(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void playFolderAudioFiles(com.tlmc.player.data.model.WebDavFile clickedFile, java.util.List<com.tlmc.player.data.model.WebDavFile> audioFiles) {
    }
    
    private final androidx.media3.common.MediaItem buildMediaItemForFile(com.tlmc.player.data.model.WebDavFile file) {
        return null;
    }
    
    private final boolean onFileLongClicked(com.tlmc.player.data.model.WebDavFile file) {
        return false;
    }
    
    private final void showFileInfo(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void openPlayerFromCue(com.tlmc.player.data.model.WebDavFile cueFile) {
    }
    
    private final void openImage(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void openText(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void connectToPlayerService() {
    }
    
    private final void onControllerConnected() {
    }
    
    private final void setupMiniPlayer() {
    }
    
    private final void updateMiniPlayerVisibility() {
    }
    
    private final void updateMiniPlayerInfo() {
    }
    
    private final void updateMiniPlayerControls() {
    }
    
    private final void updateMiniPlayer() {
    }
    
    private final void addToPlaylist(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void showPlaylistDialog() {
    }
    
    private final void refreshPlaylistDialog(androidx.media3.session.MediaController controller, androidx.recyclerview.widget.RecyclerView recyclerView, android.widget.TextView emptyView) {
    }
    
    private final void togglePlayMode() {
    }
    
    private final void applyPlayModeToController() {
    }
    
    private final void syncPlayModeFromController() {
    }
    
    private final void updatePlayModeIcon() {
    }
    
    private final void showSearchDialog() {
    }
    
    @java.lang.Override()
    public boolean onCreateOptionsMenu(@org.jetbrains.annotations.NotNull()
    android.view.Menu menu) {
        return false;
    }
    
    @java.lang.Override()
    public boolean onOptionsItemSelected(@org.jetbrains.annotations.NotNull()
    android.view.MenuItem item) {
        return false;
    }
    
    private final void showSettingsDialog() {
    }
    
    @java.lang.Override()
    @java.lang.Deprecated()
    public void onBackPressed() {
    }
}