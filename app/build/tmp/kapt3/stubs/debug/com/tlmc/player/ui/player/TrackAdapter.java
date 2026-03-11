package com.tlmc.player.ui.player;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.tlmc.player.R;
import com.tlmc.player.data.model.CueTrack;
import com.tlmc.player.databinding.ItemTrackBinding;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0010\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u0012\u0012\u0004\u0012\u00020\u0002\u0012\b\u0012\u00060\u0003R\u00020\u00000\u0001:\u0002\u0013\u0014B\u0019\u0012\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005\u00a2\u0006\u0002\u0010\bJ\u001c\u0010\n\u001a\u00020\u00072\n\u0010\u000b\u001a\u00060\u0003R\u00020\u00002\u0006\u0010\f\u001a\u00020\u0006H\u0016J\u001c\u0010\r\u001a\u00060\u0003R\u00020\u00002\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0006H\u0016J\u000e\u0010\u0011\u001a\u00020\u00072\u0006\u0010\u0012\u001a\u00020\u0006R\u000e\u0010\t\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u00070\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2 = {"Lcom/tlmc/player/ui/player/TrackAdapter;", "Landroidx/recyclerview/widget/ListAdapter;", "Lcom/tlmc/player/data/model/CueTrack;", "Lcom/tlmc/player/ui/player/TrackAdapter$TrackViewHolder;", "onTrackClick", "Lkotlin/Function1;", "", "", "(Lkotlin/jvm/functions/Function1;)V", "currentTrackIndex", "onBindViewHolder", "holder", "position", "onCreateViewHolder", "parent", "Landroid/view/ViewGroup;", "viewType", "setCurrentTrack", "index", "TrackDiffCallback", "TrackViewHolder", "app_debug"})
public final class TrackAdapter extends androidx.recyclerview.widget.ListAdapter<com.tlmc.player.data.model.CueTrack, com.tlmc.player.ui.player.TrackAdapter.TrackViewHolder> {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function1<java.lang.Integer, kotlin.Unit> onTrackClick = null;
    private int currentTrackIndex = -1;
    
    public TrackAdapter(@org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super java.lang.Integer, kotlin.Unit> onTrackClick) {
        super(null);
    }
    
    public final void setCurrentTrack(int index) {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.tlmc.player.ui.player.TrackAdapter.TrackViewHolder onCreateViewHolder(@org.jetbrains.annotations.NotNull()
    android.view.ViewGroup parent, int viewType) {
        return null;
    }
    
    @java.lang.Override()
    public void onBindViewHolder(@org.jetbrains.annotations.NotNull()
    com.tlmc.player.ui.player.TrackAdapter.TrackViewHolder holder, int position) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0003J\u0018\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00022\u0006\u0010\u0007\u001a\u00020\u0002H\u0016J\u0018\u0010\b\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00022\u0006\u0010\u0007\u001a\u00020\u0002H\u0016\u00a8\u0006\t"}, d2 = {"Lcom/tlmc/player/ui/player/TrackAdapter$TrackDiffCallback;", "Landroidx/recyclerview/widget/DiffUtil$ItemCallback;", "Lcom/tlmc/player/data/model/CueTrack;", "()V", "areContentsTheSame", "", "oldItem", "newItem", "areItemsTheSame", "app_debug"})
    public static final class TrackDiffCallback extends androidx.recyclerview.widget.DiffUtil.ItemCallback<com.tlmc.player.data.model.CueTrack> {
        
        public TrackDiffCallback() {
            super();
        }
        
        @java.lang.Override()
        public boolean areItemsTheSame(@org.jetbrains.annotations.NotNull()
        com.tlmc.player.data.model.CueTrack oldItem, @org.jetbrains.annotations.NotNull()
        com.tlmc.player.data.model.CueTrack newItem) {
            return false;
        }
        
        @java.lang.Override()
        public boolean areContentsTheSame(@org.jetbrains.annotations.NotNull()
        com.tlmc.player.data.model.CueTrack oldItem, @org.jetbrains.annotations.NotNull()
        com.tlmc.player.data.model.CueTrack newItem) {
            return false;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\b\u0086\u0004\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/tlmc/player/ui/player/TrackAdapter$TrackViewHolder;", "Landroidx/recyclerview/widget/RecyclerView$ViewHolder;", "binding", "Lcom/tlmc/player/databinding/ItemTrackBinding;", "(Lcom/tlmc/player/ui/player/TrackAdapter;Lcom/tlmc/player/databinding/ItemTrackBinding;)V", "bind", "", "track", "Lcom/tlmc/player/data/model/CueTrack;", "position", "", "app_debug"})
    public final class TrackViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @org.jetbrains.annotations.NotNull()
        private final com.tlmc.player.databinding.ItemTrackBinding binding = null;
        
        public TrackViewHolder(@org.jetbrains.annotations.NotNull()
        com.tlmc.player.databinding.ItemTrackBinding binding) {
            super(null);
        }
        
        public final void bind(@org.jetbrains.annotations.NotNull()
        com.tlmc.player.data.model.CueTrack track, int position) {
        }
    }
}