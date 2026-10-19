package com.myiptv.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.myiptv.app.R
import com.myiptv.app.data.Channel
import com.myiptv.app.data.ChannelNames
import com.myiptv.app.data.ChannelRow
import com.myiptv.app.data.ContentType
import com.myiptv.app.databinding.ItemChannelBinding

/**
 * The live / movies / series grid.
 *
 * Backed by [ListAdapter], so a new filtered list is diffed on a background
 * thread and only the rows that actually changed are re-bound. With providers
 * exposing tens of thousands of items, the old "clear and rebind everything on
 * every keystroke" approach is what made the list stutter.
 */
class ChannelAdapter(
    private val typeLabel: (ContentType) -> String,
    private val onPlay: (Channel) -> Unit,
    private val onToggleFavorite: (Channel) -> Unit
) : ListAdapter<ChannelRow, ChannelAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val item = row.channel
        val b = holder.binding

        b.channelName.text = ChannelNames.display(item.name)
        b.channelMeta.text = if (item.category.isNotBlank()) {
            "${typeLabel(item.type)} · ${item.category}"
        } else {
            typeLabel(item.type)
        }
        b.favIndicator.visibility = if (row.favorite) View.VISIBLE else View.GONE

        Glide.with(b.channelLogo)
            .load(item.logo)
            // Posters and logos arrive far larger than the tile; halving the
            // memory each one takes is what keeps scrolling smooth on TV boxes.
            .format(DecodeFormat.PREFER_RGB_565)
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

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChannelRow>() {
            override fun areItemsTheSame(oldItem: ChannelRow, newItem: ChannelRow): Boolean =
                oldItem.channel.id == newItem.channel.id

            override fun areContentsTheSame(oldItem: ChannelRow, newItem: ChannelRow): Boolean =
                oldItem == newItem
        }
    }
}
