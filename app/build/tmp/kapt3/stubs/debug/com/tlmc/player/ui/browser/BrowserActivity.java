package com.tlmc.player.ui.browser;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tlmc.player.R;
import com.tlmc.player.data.model.ServerConfig;
import com.tlmc.player.data.model.WebDavFile;
import com.tlmc.player.databinding.ActivityBrowserBinding;
import com.tlmc.player.ui.image.ImageActivity;
import com.tlmc.player.ui.player.PlayerActivity;
import com.tlmc.player.ui.text.TextActivity;
import dagger.hilt.android.AndroidEntryPoint;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\n\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\r\u001a\u00020\u000eH\u0002J\b\u0010\u000f\u001a\u00020\u000eH\u0017J\u0012\u0010\u0010\u001a\u00020\u000e2\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012H\u0014J\u0010\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0016H\u0016J\u0010\u0010\u0017\u001a\u00020\u000e2\u0006\u0010\u0018\u001a\u00020\u0019H\u0002J\u0010\u0010\u001a\u001a\u00020\u00142\u0006\u0010\u0018\u001a\u00020\u0019H\u0002J\u0010\u0010\u001b\u001a\u00020\u00142\u0006\u0010\u001c\u001a\u00020\u001dH\u0016J\u0010\u0010\u001e\u001a\u00020\u000e2\u0006\u0010\u0018\u001a\u00020\u0019H\u0002J\u0010\u0010\u001f\u001a\u00020\u000e2\u0006\u0010\u0018\u001a\u00020\u0019H\u0002J\u0010\u0010 \u001a\u00020\u000e2\u0006\u0010!\u001a\u00020\u0019H\u0002J\u0010\u0010\"\u001a\u00020\u000e2\u0006\u0010\u0018\u001a\u00020\u0019H\u0002J\b\u0010#\u001a\u00020\u000eH\u0002J\b\u0010$\u001a\u00020\u000eH\u0002J\b\u0010%\u001a\u00020\u000eH\u0002J\b\u0010&\u001a\u00020\u000eH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0007\u001a\u00020\b8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u000b\u0010\f\u001a\u0004\b\t\u0010\n\u00a8\u0006\'"}, d2 = {"Lcom/tlmc/player/ui/browser/BrowserActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "binding", "Lcom/tlmc/player/databinding/ActivityBrowserBinding;", "fileAdapter", "Lcom/tlmc/player/ui/browser/FileAdapter;", "viewModel", "Lcom/tlmc/player/ui/browser/BrowserViewModel;", "getViewModel", "()Lcom/tlmc/player/ui/browser/BrowserViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "observeViewModel", "", "onBackPressed", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onCreateOptionsMenu", "", "menu", "Landroid/view/Menu;", "onFileClicked", "file", "Lcom/tlmc/player/data/model/WebDavFile;", "onFileLongClicked", "onOptionsItemSelected", "item", "Landroid/view/MenuItem;", "openImage", "openPlayer", "openPlayerFromCue", "cueFile", "openText", "requestNotificationPermission", "setupRecyclerView", "setupSwipeRefresh", "showSettingsDialog", "app_debug"})
public final class BrowserActivity extends androidx.appcompat.app.AppCompatActivity {
    private com.tlmc.player.databinding.ActivityBrowserBinding binding;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    private com.tlmc.player.ui.browser.FileAdapter fileAdapter;
    
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
    
    private final void requestNotificationPermission() {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void setupSwipeRefresh() {
    }
    
    private final void observeViewModel() {
    }
    
    private final void onFileClicked(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final boolean onFileLongClicked(com.tlmc.player.data.model.WebDavFile file) {
        return false;
    }
    
    private final void openPlayer(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void openPlayerFromCue(com.tlmc.player.data.model.WebDavFile cueFile) {
    }
    
    private final void openImage(com.tlmc.player.data.model.WebDavFile file) {
    }
    
    private final void openText(com.tlmc.player.data.model.WebDavFile file) {
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