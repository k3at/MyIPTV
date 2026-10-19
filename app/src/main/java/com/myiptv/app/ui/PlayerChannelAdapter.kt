package com.myiptv.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.myiptv.app.R
import com.myiptv.app.data.Channel
import com.myiptv.app.data.ChannelNames
import com.myiptv.app.databinding.ItemPlayerChannelBinding

class PlayerChannelAdapter(
    private val channels: List<Channel>,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<PlayerChannelAdapter.ViewHolder>() {

    private var currentId: String? = null

    fun setCurrent(id: String?) {
        if (id == currentId) return
        currentId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlayerChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = channels.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = channels[position]
        val b = holder.binding
        val isCurrent = item.id == currentId

        b.pcName.text = ChannelNames.display(item.name)
        b.pcPlaying.visibility = if (isCurrent) View.VISIBLE else View.GONE
        b.pcRoot.isSelected = isCurrent

        Glide.with(b.pcLogo)
            .load(item.logo)
            .placeholder(R.drawable.placeholder_channel)
            .error(R.drawable.placeholder_channel)
            .into(b.pcLogo)

        b.pcRoot.setOnClickListener { onClick(item) }
    }

    class ViewHolder(val binding: ItemPlayerChannelBinding) : RecyclerView.ViewHolder(binding.root)
}
