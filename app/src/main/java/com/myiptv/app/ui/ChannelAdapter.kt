package com.myiptv.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.myiptv.app.R
import com.myiptv.app.data.Channel
import com.myiptv.app.data.label
import com.myiptv.app.databinding.ItemChannelBinding

class ChannelAdapter(
    private val onPlay: (Channel) -> Unit,
    private val onToggleFavorite: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    private val items = ArrayList<Channel>()
    private var favorites: Set<String> = emptySet()

    fun submit(list: List<Channel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setFavorites(ids: Set<String>) {
        favorites = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.channelName.text = item.name
        b.channelMeta.text = if (item.category.isNotBlank()) {
            "${item.type.label} · ${item.category}"
        } else {
            item.type.label
        }
        b.favIndicator.visibility = if (favorites.contains(item.id)) View.VISIBLE else View.GONE

        Glide.with(b.channelLogo)
            .load(item.logo)
            .placeholder(R.drawable.placeholder_channel)
            .error(R.drawable.placeholder_channel)
            .into(b.channelLogo)

        b.root.setOnClickListener { onPlay(item) }
        b.root.setOnLongClickListener { onToggleFavorite(item); true }
        b.root.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.04f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
    }

    class ViewHolder(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)
}
