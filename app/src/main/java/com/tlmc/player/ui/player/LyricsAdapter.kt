package com.tlmc.player.ui.player

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tlmc.player.R
import com.tlmc.player.data.model.LrcLine

class LyricsAdapter(
    private val onLineClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    private var lines: List<LrcLine> = emptyList()
    private var currentIndex: Int = -1

    fun setLyrics(lyrics: List<LrcLine>) {
        lines = lyrics
        currentIndex = -1
        notifyDataSetChanged()
    }

    fun setCurrentLine(index: Int) {
        if (index == currentIndex) return
        val old = currentIndex
        currentIndex = index
        if (old >= 0 && old < lines.size) notifyItemChanged(old)
        if (index >= 0 && index < lines.size) notifyItemChanged(index)
    }

    override fun getItemCount(): Int = lines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_line, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        holder.textView.text = line.text.ifEmpty { "···" }

        val isCurrent = position == currentIndex
        holder.textView.apply {
            alpha = if (isCurrent) 1.0f else 0.5f
            setTypeface(null, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
            textSize = if (isCurrent) 17f else 15f
        }

        holder.itemView.setOnClickListener {
            onLineClick?.invoke(position)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.lyricText)
    }
}

