package com.myiptv.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.myiptv.app.data.Episode
import com.myiptv.app.databinding.ItemEpisodeBinding

/** Episode list for the series screen. */
class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

    private val items = ArrayList<Episode>()

    fun submit(list: List<Episode>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.epBadge.text = "S${item.season}E${item.number}"
        b.epTitle.text = item.title
        b.epPlot.text = item.plot
        b.epPlot.visibility = if (item.plot.isNullOrBlank()) View.GONE else View.VISIBLE

        b.root.setOnClickListener { onClick(item) }
        b.root.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.02f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
    }

    class ViewHolder(val binding: ItemEpisodeBinding) : RecyclerView.ViewHolder(binding.root)
}
